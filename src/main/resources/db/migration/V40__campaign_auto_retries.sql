-- Retell "Automatic retries". Off for every campaign that exists, so none starts dialling twice
-- or rescheduling callbacks on its own; a new empty draft is created with both on.
ALTER TABLE campaign ADD COLUMN double_dial BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE campaign ADD COLUMN auto_schedule_callbacks BOOLEAN NOT NULL DEFAULT false;
-- A sequence campaign already kept the callback time the agent booked; it keeps doing so.
UPDATE campaign SET auto_schedule_callbacks = true WHERE sequence IS NOT NULL;

-- The attempt a target was double dialled on, so the second dial of that attempt is never doubled again.
-- Compared with campaign_target.attempts; no reset needed when the next attempt is placed.
ALTER TABLE campaign_target ADD COLUMN redialed_at_attempt INT;
