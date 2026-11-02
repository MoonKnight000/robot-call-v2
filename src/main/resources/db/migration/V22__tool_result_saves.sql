-- What a tool's 2xx answer leaves behind: the arguments the model searched with, saved as call
-- variables (argument -> variable), and mapped answer fields saved into client memory
-- (field -> fact). NULL saves nothing.
ALTER TABLE tool
    ADD COLUMN result_argument_variables JSONB,
    ADD COLUMN result_memory_facts       JSONB;
