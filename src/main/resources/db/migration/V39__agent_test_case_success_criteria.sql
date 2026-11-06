-- A test case's success criteria in plain words (AgentTestCase.successCriteria), judged by a model after
-- the simulated call — what Retell calls "Success Criteria". Null: nothing beyond the other expectations.
ALTER TABLE agent_test_case ADD COLUMN success_criteria TEXT;
