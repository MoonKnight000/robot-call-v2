package main

// g711Codec is the app's media codec (RtpCodec on the Java side): ULAW or ALAW, 8 kHz.
type g711Codec struct {
	payloadType uint8
	encode      func(int16) byte
	decode      func(byte) int16
}

func parseCodec(name string) (g711Codec, bool) {
	switch name {
	case "", "ULAW":
		return g711Codec{payloadType: 0, encode: ulawEncode, decode: ulawDecode}, true
	case "ALAW":
		return g711Codec{payloadType: 8, encode: alawEncode, decode: alawDecode}, true
	}
	return g711Codec{}, false
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
