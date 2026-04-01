-- V2: Create user_room_stats table
CREATE TABLE user_room_stats (
    username           VARCHAR(64) PRIMARY KEY,
    rooms_created_count INT        NOT NULL DEFAULT 0 CHECK (rooms_created_count >= 0),
    active_rooms_count  INT        NOT NULL DEFAULT 0 CHECK (active_rooms_count >= 0 AND active_rooms_count <= 10)
);
