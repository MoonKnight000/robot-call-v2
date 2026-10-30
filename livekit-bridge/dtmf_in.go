package main

import (
	"bytes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"log/slog"
	"net/http"
	"time"

	lksdk "github.com/livekit/server-sdk-go/v2"
)

// dtmfBacklog is how many digits may wait for the app before the next ones are dropped.
const dtmfBacklog = 32

var appClient = &http.Client{Timeout: 5 * time.Second}

// dtmfEvent is one key the caller pressed, as the app receives it.
type dtmfEvent struct {
	Room  string `json:"room"`
	Digit string `json:"digit"`
}

// onDataPacket picks the keypad digits the LiveKit SIP service forwards from the phone leg out of
// the room's data packets and queues them for the app.
func (c *call) onDataPacket(data lksdk.DataPacket, _ lksdk.DataReceiveParams) {
	dtmf := data.ToProto().GetSipDtmf()
	if dtmf == nil {
		return
	}
	digit := dtmf.GetDigit()
	if digit == "" {
		digit = digitOfCode(dtmf.GetCode())
	}
	if digit == "" {
		return
	}
	select {
	case c.digits <- digit:
	default:
		slog.Warn("dtmf backlog full, digit dropped", "room", c.roomName)
	}
}

// digitOfCode is the keypad character of an RFC 4733 event code.
func digitOfCode(code uint32) string {
	switch {
	case code <= 9:
		return string(rune('0' + code))
	case code == 10:
		return "*"
	case code == 11:
		return "#"
	}
	return ""
}

// reportDigits posts the queued digits to the app one at a time, so they arrive in the order
// they were pressed, until the call closes.
func (c *call) reportDigits() {
	for {
		select {
		case <-c.done:
			return
		case digit := <-c.digits:
			if c.cfg.appDTMFURL == "" {
				slog.Debug("dtmf received, no app url to report to", "room", c.roomName)
				continue
			}
			if err := c.postDigit(digit); err != nil {
				slog.Warn("dtmf not delivered", "room", c.roomName, "err", err)
			}
		}
	}
}

// postDigit sends one digit, signed with the LiveKit API secret the app shares (HMAC-SHA256 of
// the body, hex), which is how the app knows the request came from this bridge.
func (c *call) postDigit(digit string) error {
	body, err := json.Marshal(dtmfEvent{Room: c.roomName, Digit: digit})
	if err != nil {
		return err
	}
	mac := hmac.New(sha256.New, []byte(c.cfg.apiSecret))
	mac.Write(body)
	req, err := http.NewRequest(http.MethodPost, c.cfg.appDTMFURL, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Bridge-Signature", hex.EncodeToString(mac.Sum(nil)))
	resp, err := appClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		return &httpStatusError{status: resp.StatusCode}
	}
	return nil
}

type httpStatusError struct{ status int }

func (e *httpStatusError) Error() string { return "app answered HTTP " + http.StatusText(e.status) }
