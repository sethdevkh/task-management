CREATE TABLE teams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL,
    team_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT ck_users_role CHECK (role IN ('TEAM_LEAD', 'TEAM_MEMBER'))
);

CREATE TABLE tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(4000) NULL,
    status VARCHAR(32) NOT NULL,
    assignee_id BIGINT NOT NULL,
    creator_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    due_date DATE NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES users (id),
    CONSTRAINT fk_tasks_creator FOREIGN KEY (creator_id) REFERENCES users (id),
    CONSTRAINT fk_tasks_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT ck_tasks_status CHECK (status IN ('TO_DO', 'IN_PROGRESS', 'COMPLETED'))
);

CREATE INDEX idx_tasks_team_id ON tasks (team_id);
CREATE INDEX idx_tasks_assignee_id ON tasks (assignee_id);

CREATE TABLE standups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    standup_date DATE NOT NULL,
    done VARCHAR(4000) NULL,
    doing VARCHAR(4000) NULL,
    blockers VARCHAR(4000) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_standups_user_date UNIQUE (user_id, standup_date),
    CONSTRAINT fk_standups_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_standups_team FOREIGN KEY (team_id) REFERENCES teams (id)
);

CREATE INDEX idx_standups_team_date ON standups (team_id, standup_date);
