package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"github.com/livekit/protocol/livekit"
)

// dtmfSpacing is the pause between two digits. The LiveKit SIP service plays each one as a tone
// on the phone leg, and a digit sent on top of the previous one is lost to a carrier IVR.
const dtmfSpacing = 250 * time.Millisecond

type dtmfRequest struct {
	Digits string `json:"digits"`
}

// dtmfPacket is the data packet the LiveKit SIP service turns into a keypad tone on the phone leg.
type dtmfPacket struct {
	code  uint32
	digit string
}

func (p dtmfPacket) ToProto() *livekit.DataPacket {
	return &livekit.DataPacket{
		Value: &livekit.DataPacket_SipDtmf{SipDtmf: &livekit.SipDTMF{Code: p.code, Digit: p.digit}},
	}
}

// dtmfCode is the RFC 4733 event code of a keypad character.
func dtmfCode(r rune) (uint32, bool) {
	switch {
	case r >= '0' && r <= '9':
		return uint32(r - '0'), true
	case r == '*':
		return 10, true
	case r == '#':
		return 11, true
	}
	return 0, false
}

// sendDTMF presses digits on the phone leg, one at a time. Every character is checked before the
// first is sent, so a bad request presses nothing.
func (c *call) sendDTMF(digits string) error {
	packets := make([]dtmfPacket, 0, len(digits))
	for _, r := range digits {
		code, ok := dtmfCode(r)
		if !ok {
			return fmt.Errorf("unsupported DTMF digit %q", r)
		}
		packets = append(packets, dtmfPacket{code: code, digit: string(r)})
	}
	room := c.room.Load()
	if room == nil || c.closed.Load() {
		return errors.New("call is closed")
	}
	for i, packet := range packets {
		if err := room.LocalParticipant.PublishDataPacket(packet); err != nil {
			return fmt.Errorf("publish DTMF %q: %w", packet.digit, err)
		}
		if i < len(packets)-1 {
			time.Sleep(dtmfSpacing)
		}
	}
	return nil
}

func (s *server) handleDTMF(w http.ResponseWriter, r *http.Request) {
	room := r.PathValue("room")
	var req dtmfRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.Digits == "" {
		http.Error(w, "digits are required", http.StatusBadRequest)
		return
	}
	s.mu.Lock()
	c := s.calls[room]
	s.mu.Unlock()
	if c == nil {
		http.Error(w, "room is not bridged", http.StatusNotFound)
		return
	}
	if err := c.sendDTMF(req.Digits); err != nil {
		slog.Warn("dtmf failed", "room", room, "err", err)
		http.Error(w, err.Error(), http.StatusBadGateway)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
