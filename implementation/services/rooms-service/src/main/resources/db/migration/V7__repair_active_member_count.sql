-- V7: Repair active_member_count for rooms that already existed before
-- the counter was kept in sync. Recomputes each room's count from room_members.
UPDATE rooms r
SET active_member_count = (
    SELECT COUNT(*)
    FROM room_members rm
    WHERE rm.room_id = r.id
);
