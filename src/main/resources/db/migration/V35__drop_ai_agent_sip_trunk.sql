-- An agent no longer picks the trunks a campaign dials over (docs/PHONE-NUMBER-BINDING.md, phase 5):
-- the line is the campaign's — its from number's or pool number's own trunk, else every enabled
-- trunk of the company. Published snapshots that still carry "sipTrunkIds" read it as unknown and drop it.
DROP TABLE ai_agent_sip_trunk;
