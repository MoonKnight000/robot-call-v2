-- Retell-style sequence outcomes. A target records the outcome its last answered call reached
-- (the campaign's "Disposition mix") and whether it left the sequence as a conversion.
ALTER TABLE campaign_target ADD COLUMN outcome VARCHAR(120);
ALTER TABLE campaign_target ADD COLUMN converted BOOLEAN NOT NULL DEFAULT false;

-- Sequences saved before outcomes: the fixed per-step "routes" map is replaced by outcomes, so it is
-- dropped from every step, and the sequence gets Retell's three default global outcomes. A step that
-- routed somewhere now ends the sequence on every outcome until it is edited.
UPDATE campaign
SET sequence = jsonb_set(
        sequence,
        '{steps}',
        COALESCE((SELECT jsonb_agg(e.step - 'routes' ORDER BY e.ord)
                  FROM jsonb_array_elements(sequence -> 'steps') WITH ORDINALITY AS e(step, ord)), '[]'::jsonb))
    || jsonb_build_object('globalOutcomes', '[
        {"id": "do_not_call", "name": "Do Not Call", "evaluation": "PROMPT",
         "prompt": "The lead asked not to be called or contacted again.",
         "saveToContact": {"doNotCall": true, "tags": []}, "next": {"exit": "END", "delayMinutes": 0}},
        {"id": "success", "name": "Success", "evaluation": "PROMPT",
         "prompt": "The lead agreed to move forward: made a commitment, accepted the offer or booked what was asked.",
         "next": {"exit": "END_SUCCESSFUL", "delayMinutes": 0}},
        {"id": "not_interested", "name": "Not Interested", "evaluation": "PROMPT",
         "prompt": "The lead said they are not interested and declined to move forward.",
         "next": {"exit": "END", "delayMinutes": 0}}
    ]'::jsonb)
WHERE sequence IS NOT NULL
  AND jsonb_typeof(sequence) = 'object'
  AND jsonb_typeof(sequence -> 'steps') = 'array'
  AND NOT sequence ? 'globalOutcomes';
