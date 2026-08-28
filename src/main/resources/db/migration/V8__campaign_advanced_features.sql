-- 1. Fon shovqini (Ambient Soundscape): OFF, OFFICE, CALL_CENTER, NATURAL_LINE, CAFE
ALTER TABLE campaign ADD COLUMN ambient_sound VARCHAR(30) NOT NULL DEFAULT 'OFF';

-- 2. Suhbat davomida SMS yuborish (Mid-Call SMS)
ALTER TABLE campaign ADD COLUMN mid_call_sms_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE campaign ADD COLUMN mid_call_sms_template VARCHAR(500);

-- 3. Avtojavoblagich / AMD harakati: HANGUP (darhol qo'yish), LEAVE_MESSAGE (xabar qoldirish), IGNORE (oddiy davom etish)
ALTER TABLE campaign ADD COLUMN voicemail_action VARCHAR(30) NOT NULL DEFAULT 'HANGUP';
ALTER TABLE campaign ADD COLUMN voicemail_message VARCHAR(500);

-- 4. DTMF (Tugmalar orqali kiritish)
ALTER TABLE campaign ADD COLUMN dtmf_input_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- 5. Hissiyotga moslashuvchan ovoz (Emotion-Adaptive Voice)
ALTER TABLE campaign ADD COLUMN emotion_adaptive_voice BOOLEAN NOT NULL DEFAULT TRUE;
