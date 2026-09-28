-- 회원 로그인 아이디(user_id) 중복 방지
-- 중복 행이 있으면 로그인 시 "Query did not return a unique result"로 해당 계정이 로그인할 수 없게 된다.
-- 선행 조건: 기존 중복 user_id가 없어야 한다. 중복이 남아 있으면 이 마이그레이션이 실패하고 애플리케이션 기동이 중단된다.
--   확인: SELECT user_id, COUNT(*) FROM member GROUP BY user_id HAVING COUNT(*) > 1;
ALTER TABLE member ADD CONSTRAINT uk_member_user_id UNIQUE (user_id);
