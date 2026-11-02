-- How a tool's answer reaches the model: how many list elements it is shown (0 = all), and what
-- it is told instead of an empty answer or a failed request (NULL = the default behaviour).
ALTER TABLE tool
    ADD COLUMN result_max_items     INT NOT NULL DEFAULT 0,
    ADD COLUMN result_empty_message VARCHAR(500),
    ADD COLUMN result_error_message VARCHAR(500);
