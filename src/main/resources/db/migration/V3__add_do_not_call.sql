-- Right-to-refuse (PROJECT.md §11.4): a target flagged do_not_call is skipped by the
-- dialer and excluded from future campaigns.
ALTER TABLE campaign_target ADD COLUMN do_not_call BOOLEAN NOT NULL DEFAULT false;
