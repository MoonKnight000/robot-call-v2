-- Which weekdays a campaign may dial on (PROJECT.md §11.2 — "dam olish kunlari
-- alohida sozlanadi"). dial_window_start/end only constrain the time of day, so
-- without this a campaign calls debtors on Sunday morning.
--
-- Stored as a comma-separated list of java.time.DayOfWeek names; the default is
-- Monday-Friday.
ALTER TABLE campaign
    ADD COLUMN dial_days VARCHAR(64) NOT NULL
        DEFAULT 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY';
