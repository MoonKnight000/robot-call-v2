// Command livekit-bridge joins the LiveKit rooms of robot-call-v2's LiveKit-engine calls and
// carries their audio to and from the app's RTP endpoint, the way Asterisk's externalMedia
// does for an Asterisk call (docs/LIVEKIT.md). The app drives it over HTTP
// (LiveKitBridgeClient): POST /calls puts it in a room, DELETE /calls/{room} takes it out,
// POST /calls/{room}/dtmf presses keypad digits on the phone leg. In the other direction the digits
// the caller presses are posted to BRIDGE_APP_DTMF_URL (dtmf_in.go).
//
// It listens on the compose network only and has no authentication of its own.
package main

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"os"
	"sync"
)

type config struct {
	livekitURL string
	apiKey     string
	apiSecret  string
	httpAddr   string
	// The address the app sends the agent's voice to: this container as the app sees it.
	rtpHost string
	// Where the caller's keypad digits are reported (the app's LiveKit bridge endpoint); empty reports nothing.
	appDTMFURL string
}

type startRequest struct {
	Room    string `json:"room"`
	RTPHost string `json:"rtpHost"`
	RTPPort int    `json:"rtpPort"`
	Codec   string `json:"codec"`
}

type startResponse struct {
	RTPHost string `json:"rtpHost"`
	RTPPort int    `json:"rtpPort"`
}

type server struct {
	cfg   config
	mu    sync.Mutex
	calls map[string]*call
}

func main() {
	cfg := config{
		livekitURL: requireEnv("LIVEKIT_URL"),
		apiKey:     requireEnv("LIVEKIT_API_KEY"),
		apiSecret:  requireEnv("LIVEKIT_API_SECRET"),
		httpAddr:   envOr("BRIDGE_HTTP_ADDR", ":8090"),
		rtpHost:    envOr("BRIDGE_RTP_HOST", "livekit-bridge"),
		appDTMFURL: os.Getenv("BRIDGE_APP_DTMF_URL"),
	}
	s := &server{cfg: cfg, calls: map[string]*call{}}
	mux := http.NewServeMux()
	mux.HandleFunc("POST /calls", s.handleStart)
	mux.HandleFunc("DELETE /calls/{room}", s.handleStop)
	mux.HandleFunc("POST /calls/{room}/dtmf", s.handleDTMF)
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusOK) })
	slog.Info("livekit-bridge listening", "addr", cfg.httpAddr, "livekit", cfg.livekitURL)
	if err := http.ListenAndServe(cfg.httpAddr, mux); err != nil {
		slog.Error("http server stopped", "err", err)
		os.Exit(1)
	}
}

func (s *server) handleStart(w http.ResponseWriter, r *http.Request) {
	var req startRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.Room == "" || req.RTPHost == "" || req.RTPPort <= 0 {
		http.Error(w, "room, rtpHost and rtpPort are required", http.StatusBadRequest)
		return
	}
	codec, ok := parseCodec(req.Codec)
	if !ok {
		http.Error(w, "codec must be ULAW or ALAW", http.StatusBadRequest)
		return
	}
	s.mu.Lock()
	_, exists := s.calls[req.Room]
	s.mu.Unlock()
	if exists {
		http.Error(w, "room is already bridged", http.StatusConflict)
		return
	}

	var c *call
	c, err := startCall(s.cfg, req.Room, req.RTPHost, req.RTPPort, codec, func() { s.forget(req.Room, c) })
	if err != nil {
		slog.Error("bridge start failed", "room", req.Room, "err", err)
		http.Error(w, err.Error(), http.StatusBadGateway)
		return
	}
	s.mu.Lock()
	s.calls[req.Room] = c
	s.mu.Unlock()
	slog.Info("bridged", "room", req.Room, "app", req.RTPHost, "appPort", req.RTPPort, "port", c.localPort())

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(startResponse{RTPHost: s.cfg.rtpHost, RTPPort: c.localPort()})
}

func (s *server) handleStop(w http.ResponseWriter, r *http.Request) {
	room := r.PathValue("room")
	s.mu.Lock()
	c := s.calls[room]
	s.mu.Unlock()
	if c == nil {
		http.Error(w, "room is not bridged", http.StatusNotFound)
		return
	}
	c.close()
	w.WriteHeader(http.StatusNoContent)
}

// forget drops room's entry, unless it already belongs to a newer call.
func (s *server) forget(room string, c *call) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.calls[room] == c {
		delete(s.calls, room)
	}
	slog.Info("unbridged", "room", room)
}

func requireEnv(name string) string {
	v := os.Getenv(name)
	if v == "" {
		slog.Error("missing environment variable", "name", name)
		os.Exit(1)
	}
	return v
}

func envOr(name, fallback string) string {
	if v := os.Getenv(name); v != "" {
		return v
	}
	return fallback
}
