package main

import (
	"fmt"
	"log/slog"
	"math/rand/v2"
	"net"
	"strconv"
	"sync"
	"sync/atomic"
	"time"

	lksdk "github.com/livekit/server-sdk-go/v2"
	"github.com/pion/rtp"
	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
	"gopkg.in/hraban/opus.v2"
)

const (
	// The app's media leg is G.711 at 8 kHz; libopus decodes to and encodes from that rate
	// directly, so nothing is resampled here.
	sampleRate    = 8000
	frameSamples  = 160 // 20 ms
	frameDuration = 20 * time.Millisecond
	// The longest Opus frame, 120 ms.
	maxOpusFrameSamples = sampleRate * 120 / 1000
	agentIdentity       = "robot-call-agent"
)

// call is one room's bridge: the phone leg's audio to the app as RTP, and the app's RTP back
// into the room as the agent's track.
type call struct {
	roomName string
	room     atomic.Pointer[lksdk.Room]
	conn     *net.UDPConn
	peer     *net.UDPAddr
	codec    g711Codec
	track    *lksdk.LocalSampleTrack
	onClose  func()
	closed   atomic.Bool
	cfg      config
	// Keys the caller pressed, waiting to be reported to the app; done ends the reporter.
	digits chan string
	done   chan struct{}

	// RTP towards the app; one sender at a time.
	sendMu  sync.Mutex
	seq     uint16
	ts      uint32
	ssrc    uint32
	pending []int16
}

func startCall(cfg config, roomName, rtpHost string, rtpPort int, codec g711Codec, onClose func()) (*call, error) {
	peer, err := net.ResolveUDPAddr("udp", net.JoinHostPort(rtpHost, strconv.Itoa(rtpPort)))
	if err != nil {
		return nil, fmt.Errorf("resolve %s:%d: %w", rtpHost, rtpPort, err)
	}
	conn, err := net.ListenUDP("udp", &net.UDPAddr{})
	if err != nil {
		return nil, fmt.Errorf("open RTP socket: %w", err)
	}
	track, err := lksdk.NewLocalSampleTrack(webrtc.RTPCodecCapability{
		MimeType: webrtc.MimeTypeOpus, ClockRate: 48000, Channels: 2,
	})
	if err != nil {
		_ = conn.Close()
		return nil, fmt.Errorf("create track: %w", err)
	}
	c := &call{
		roomName: roomName,
		conn:     conn,
		peer:     peer,
		codec:    codec,
		track:    track,
		onClose:  onClose,
		cfg:      cfg,
		digits:   make(chan string, dtmfBacklog),
		done:     make(chan struct{}),
		seq:      uint16(rand.UintN(1 << 16)),
		ts:       rand.Uint32(),
		ssrc:     rand.Uint32(),
	}

	cb := lksdk.NewRoomCallback()
	cb.ParticipantCallback.OnTrackSubscribed = c.onTrackSubscribed
	cb.ParticipantCallback.OnDataPacket = c.onDataPacket
	// The room closing (the app deleted it, everyone left) ends the bridge too.
	cb.OnDisconnected = c.close
	room, err := lksdk.ConnectToRoom(cfg.livekitURL, lksdk.ConnectInfo{
		APIKey:              cfg.apiKey,
		APISecret:           cfg.apiSecret,
		RoomName:            roomName,
		ParticipantIdentity: agentIdentity,
		ParticipantName:     "AI agent",
	}, cb)
	if err != nil {
		_ = conn.Close()
		return nil, fmt.Errorf("join room %s: %w", roomName, err)
	}
	c.room.Store(room)
	if _, err := room.LocalParticipant.PublishTrack(track, &lksdk.TrackPublicationOptions{Name: "agent-voice"}); err != nil {
		c.close()
		return nil, fmt.Errorf("publish track in %s: %w", roomName, err)
	}
	go c.forwardToRoom()
	go c.reportDigits()
	return c, nil
}

func (c *call) localPort() int {
	return c.conn.LocalAddr().(*net.UDPAddr).Port
}

// close is safe to call any number of times, including from the room's own disconnect callback.
func (c *call) close() {
	if !c.closed.CompareAndSwap(false, true) {
		return
	}
	close(c.done)
	_ = c.conn.Close()
	if room := c.room.Load(); room != nil {
		room.Disconnect()
	}
	c.onClose()
}

// onTrackSubscribed starts relaying a remote audio track — the phone leg's — to the app.
func (c *call) onTrackSubscribed(track *webrtc.TrackRemote, _ *lksdk.RemoteTrackPublication, rp *lksdk.RemoteParticipant) {
	if track.Kind() != webrtc.RTPCodecTypeAudio {
		return
	}
	slog.Info("relaying phone audio", "room", c.roomName, "participant", rp.Identity(), "codec", track.Codec().MimeType)
	go c.forwardToApp(track)
}

// forwardToApp decodes the room's Opus and sends it to the app as 20 ms G.711 RTP packets.
func (c *call) forwardToApp(track *webrtc.TrackRemote) {
	dec, err := opus.NewDecoder(sampleRate, 1)
	if err != nil {
		slog.Error("opus decoder", "room", c.roomName, "err", err)
		return
	}
	pcm := make([]int16, maxOpusFrameSamples)
	for {
		pkt, _, err := track.ReadRTP()
		if err != nil {
			return // track ended or the room closed
		}
		if len(pkt.Payload) == 0 {
			continue
		}
		n, err := dec.Decode(pkt.Payload, pcm)
		if err != nil {
			slog.Debug("opus decode", "room", c.roomName, "err", err)
			continue
		}
		c.sendToApp(pcm[:n])
	}
}

func (c *call) sendToApp(samples []int16) {
	c.sendMu.Lock()
	defer c.sendMu.Unlock()
	c.pending = append(c.pending, samples...)
	for len(c.pending) >= frameSamples {
		payload := make([]byte, frameSamples)
		for i, s := range c.pending[:frameSamples] {
			payload[i] = c.codec.encode(s)
		}
		c.pending = c.pending[frameSamples:]
		pkt := rtp.Packet{
			Header: rtp.Header{
				Version:        2,
				PayloadType:    c.codec.payloadType,
				SequenceNumber: c.seq,
				Timestamp:      c.ts,
				SSRC:           c.ssrc,
			},
			Payload: payload,
		}
		c.seq++
		c.ts += frameSamples
		buf, err := pkt.Marshal()
		if err != nil {
			continue
		}
		if _, err := c.conn.WriteToUDP(buf, c.peer); err != nil && !c.closed.Load() {
			slog.Debug("rtp send", "room", c.roomName, "err", err)
		}
	}
}

// forwardToRoom reads the app's G.711 RTP (the agent's voice, already paced at 20 ms by the
// app's endpoint) and publishes it into the room as Opus.
func (c *call) forwardToRoom() {
	enc, err := opus.NewEncoder(sampleRate, 1, opus.AppVoIP)
	if err != nil {
		slog.Error("opus encoder", "room", c.roomName, "err", err)
		c.close()
		return
	}
	buf := make([]byte, 1500)
	out := make([]byte, 1275) // the largest Opus packet
	var frame []int16
	for {
		n, _, err := c.conn.ReadFromUDP(buf)
		if err != nil {
			return // socket closed
		}
		var pkt rtp.Packet
		if err := pkt.Unmarshal(buf[:n]); err != nil {
			continue
		}
		for _, b := range pkt.Payload {
			frame = append(frame, c.codec.decode(b))
		}
		for len(frame) >= frameSamples {
			m, err := enc.Encode(frame[:frameSamples], out)
			frame = frame[frameSamples:]
			if err != nil {
				slog.Debug("opus encode", "room", c.roomName, "err", err)
				continue
			}
			data := make([]byte, m)
			copy(data, out[:m])
			if err := c.track.WriteSample(media.Sample{Data: data, Duration: frameDuration}, nil); err != nil {
				slog.Debug("write sample", "room", c.roomName, "err", err)
			}
		}
	}
}
