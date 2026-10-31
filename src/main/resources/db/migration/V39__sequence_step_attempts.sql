-- Retell-style attempts: a CONVERSATION step lists its calls, each with its own delay after the previous
-- one, instead of maxAttempts + one retryDelayMinutes for all. A saved step keeps what it did: as many
-- attempts as maxAttempts (3 when unset), the first immediate, the rest retryDelayMinutes apart
-- (0 = the campaign's standard retry delays, as before).
UPDATE campaign
SET sequence = jsonb_set(
        sequence,
        '{steps}',
        (SELECT jsonb_agg(
                        CASE
                            WHEN e.step ->> 'type' = 'CONVERSATION' AND jsonb_typeof(e.step -> 'conversation') = 'object'
                                AND NOT (e.step -> 'conversation') ? 'attempts'
                                THEN jsonb_set(e.step, '{conversation}', jsonb_build_object(
                                    'aiAgentId', e.step -> 'conversation' -> 'aiAgentId',
                                    'attempts', (SELECT jsonb_agg(jsonb_build_object(
                                                         'delay', CASE WHEN i = 1 THEN 0
                                                                       ELSE COALESCE((e.step -> 'conversation' ->> 'retryDelayMinutes')::int, 0) END,
                                                         'unit', 'MINUTES') ORDER BY i)
                                                 FROM generate_series(1, CASE
                                                     WHEN COALESCE((e.step -> 'conversation' ->> 'maxAttempts')::int, 0) > 0
                                                         THEN (e.step -> 'conversation' ->> 'maxAttempts')::int
                                                     ELSE 3 END) AS i)))
                            ELSE e.step
                            END ORDER BY e.ord)
         FROM jsonb_array_elements(sequence -> 'steps') WITH ORDINALITY AS e(step, ord)))
WHERE sequence IS NOT NULL
  AND jsonb_typeof(sequence) = 'object'
  AND jsonb_typeof(sequence -> 'steps') = 'array'
  AND jsonb_array_length(sequence -> 'steps') > 0;
