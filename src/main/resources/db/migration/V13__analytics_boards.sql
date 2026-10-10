-- Analytics Boards and Custom Query (COMPETITOR-GAP-ANALYSIS §3.5)
-- Allows companies to create custom analytics dashboards (boards) with customizable widgets
-- (KPI cards, charts, breakdowns) and provides a flexible query endpoint.

CREATE TABLE analytics_board (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES company(id),
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(255),
    is_default   BOOLEAN      NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_analytics_board_company ON analytics_board(company_id);

CREATE TABLE analytics_board_widget (
    id            BIGSERIAL PRIMARY KEY,
    board_id      BIGINT       NOT NULL REFERENCES analytics_board(id) ON DELETE CASCADE,
    title         VARCHAR(100) NOT NULL,
    widget_type   VARCHAR(32)  NOT NULL,
    metric        VARCHAR(64)  NOT NULL,
    group_by      VARCHAR(32),
    time_range    VARCHAR(32)  NOT NULL DEFAULT 'LAST_7_DAYS',
    grid_row      INT          NOT NULL DEFAULT 0,
    grid_col      INT          NOT NULL DEFAULT 0,
    grid_width    INT          NOT NULL DEFAULT 6,
    grid_height   INT          NOT NULL DEFAULT 4,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_analytics_board_widget_board ON analytics_board_widget(board_id);
