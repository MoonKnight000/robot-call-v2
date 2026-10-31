-- Extract hardcoded outcome statuses from crm_settings into relational mapping table
CREATE TABLE crm_status_mapping (
    company_id  BIGINT      NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    disposition VARCHAR(64) NOT NULL,
    status_id   INTEGER     NOT NULL,
    PRIMARY KEY (company_id, disposition)
);

INSERT INTO crm_status_mapping (company_id, disposition, status_id)
SELECT company_id, 'MEETING_BOOKED', meeting_booked_status_id FROM crm_settings WHERE meeting_booked_status_id IS NOT NULL
UNION ALL
SELECT company_id, 'PROMISE_TO_PAY', promise_to_pay_status_id FROM crm_settings WHERE promise_to_pay_status_id IS NOT NULL
UNION ALL
SELECT company_id, 'NOT_INTERESTED', not_interested_status_id FROM crm_settings WHERE not_interested_status_id IS NOT NULL
UNION ALL
SELECT company_id, 'CALLBACK_REQUESTED', callback_status_id FROM crm_settings WHERE callback_status_id IS NOT NULL
UNION ALL
SELECT company_id, 'NEW_LEAD', new_lead_status_id FROM crm_settings WHERE new_lead_status_id IS NOT NULL;

ALTER TABLE crm_settings
    DROP COLUMN meeting_booked_status_id,
    DROP COLUMN promise_to_pay_status_id,
    DROP COLUMN not_interested_status_id,
    DROP COLUMN callback_status_id,
    DROP COLUMN new_lead_status_id;
