package main

// mediaCodec is the codec of the leg to the app: ULAW or ALAW at 8 kHz (RtpCodec on the Java
// side), or SLIN16 — 16 kHz linear PCM, big-endian, payload type 118 — for a browser room, which
// carries wideband audio the app's recognizer can use (RtpEndpoint's wideband mode).
type mediaCodec struct {
	payloadType uint8
	sampleRate  int
	encode      func([]int16) []byte
	decode      func([]byte) []int16
}

// frameSamples is one 20 ms frame at the codec's rate.
func (c mediaCodec) frameSamples() int {
	return c.sampleRate / 50
}

func parseCodec(name string) (mediaCodec, bool) {
	switch name {
	case "", "ULAW":
		return mediaCodec{payloadType: 0, sampleRate: 8000, encode: perByte(ulawEncode), decode: fromBytes(ulawDecode)}, true
	case "ALAW":
		return mediaCodec{payloadType: 8, sampleRate: 8000, encode: perByte(alawEncode), decode: fromBytes(alawDecode)}, true
	case "SLIN16":
		return mediaCodec{payloadType: 118, sampleRate: 16000, encode: slinEncode, decode: slinDecode}, true
	}
	return mediaCodec{}, false
}

func perByte(encode func(int16) byte) func([]int16) []byte {
	return func(samples []int16) []byte {
		out := make([]byte, len(samples))
		for i, s := range samples {
			out[i] = encode(s)
		}
		return out
	}
}

func fromBytes(decode func(byte) int16) func([]byte) []int16 {
	return func(payload []byte) []int16 {
		out := make([]int16, len(payload))
		for i, b := range payload {
			out[i] = decode(b)
		}
		return out
	}
}

func slinEncode(samples []int16) []byte {
	out := make([]byte, len(samples)*2)
	for i, s := range samples {
		out[2*i] = byte(uint16(s) >> 8)
		out[2*i+1] = byte(s)
	}
	return out
}

func slinDecode(payload []byte) []int16 {
	out := make([]int16, len(payload)/2)
	for i := range out {
		out[i] = int16(uint16(payload[2*i])<<8 | uint16(payload[2*i+1]))
	}
	return out
}

const (
	ulawBias = 0x84
	ulawClip = 32635
)

func ulawEncode(s int16) byte {
	sample := int(s)
	sign := 0
	if sample < 0 {
		sample = -sample
		sign = 0x80
	}
	if sample > ulawClip {
		sample = ulawClip
	}
	sample += ulawBias
	exponent := 7
	for mask := 0x4000; sample&mask == 0 && exponent > 0; mask >>= 1 {
		exponent--
	}
	mantissa := (sample >> (exponent + 3)) & 0x0F
	return ^byte(sign | exponent<<4 | mantissa)
}

func ulawDecode(u byte) int16 {
	u = ^u
	t := ((int(u&0x0F) << 3) + ulawBias) << ((u & 0x70) >> 4)
	if u&0x80 != 0 {
		return int16(ulawBias - t)
	}
	return int16(t - ulawBias)
}

var alawSegmentEnds = [8]int{0x1F, 0x3F, 0x7F, 0xFF, 0x1FF, 0x3FF, 0x7FF, 0xFFF}

func alawEncode(s int16) byte {
	pcm := int(s) >> 3 // 13-bit
	mask := 0xD5
	if pcm < 0 {
		mask = 0x55
		pcm = -pcm - 1
	}
	seg := 0
	for seg < 8 && pcm > alawSegmentEnds[seg] {
		seg++
	}
	if seg >= 8 {
		return byte(0x7F ^ mask)
	}
	aval := seg << 4
	if seg < 2 {
		aval |= (pcm >> 1) & 0x0F
	} else {
		aval |= (pcm >> seg) & 0x0F
	}
	return byte(aval ^ mask)
}

func alawDecode(a byte) int16 {
	a ^= 0x55
	t := int(a&0x0F) << 4
	seg := int(a&0x70) >> 4
	switch seg {
	case 0:
		t += 8
	case 1:
		t += 0x108
	default:
		t += 0x108
		t <<= seg - 1
	}
	if a&0x80 != 0 {
		return int16(t)
	}
	return int16(-t)
}
