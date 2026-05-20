-- V7__Add_Email_To_Users.sql
-- WHAT: Agrega columna email a users para autenticación por email.
-- WHY: El frontend Flutter envía email+password. El backend debe aceptar email
--      como identificador primario de autenticación. Spring Security usará
--      User.getUsername() → email para que JWT sub = email automáticamente.
-- DIFFERENCES: V5 creó la tabla users con solo username. V7 agrega email con
--              constraint UNIQUE NOT NULL. Usuarios existentes reciben email
--              fallback = username@mundolimpio.com.

ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255);
UPDATE users SET email = username || '@mundolimpio.com' WHERE email IS NULL;
ALTER TABLE users ALTER COLUMN email SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_users_email'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email);
    END IF;
END $$;
