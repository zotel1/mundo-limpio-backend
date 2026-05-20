-- V8__Add_User_Roles.sql
-- WHAT: Crea la tabla user_roles para soportar asignacion multiple de roles
--        (relacion 1:N entre users y roles). Migra los usuarios OPERATOR existentes
--        a SALES_CLERK.
-- WHY: El modelo RBAC se expande de 2 roles a 6 roles con asignacion multiple.
--      Un usuario puede tener varios roles simultaneamente (ej: STOCK_MANAGER y
--      SALES_CLERK). La tabla intermedia user_roles reemplaza la columna role
--      en la tabla users.
-- DIFFERENCES: Antes la columna role en users almacenaba un solo VARCHAR.
--              Ahora user_roles soporta N roles por usuario con FK a users.
--              OPERATOR se migra a SALES_CLERK por ser su sucesor semantico
--              (OPERATOR originalmente solo tenia acceso a ventas).

-- 1. Crear tabla user_roles (idempotente: IF NOT EXISTS)
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE
);

-- 2. Migrar datos existentes: copiar el rol de la columna 'role' en users
--    a la nueva tabla user_roles. Solo para usuarios que tengan rol no-nulo
--    y que no tengan ya una entrada en user_roles (idempotente).
--    Los usuarios con rol 'OPERATOR' se migran como 'SALES_CLERK'.
INSERT INTO user_roles (user_id, role)
SELECT u.id,
       CASE WHEN u.role = 'OPERATOR' THEN 'SALES_CLERK' ELSE u.role END
FROM users u
WHERE u.role IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role =
          CASE WHEN u.role = 'OPERATOR' THEN 'SALES_CLERK' ELSE u.role END
  );

-- 3. Hacer nullable la columna role en users porque los roles ahora viven en
--    user_roles. El entity User ya no mapea esta columna (usa @ElementCollection).
--    Si no hacemos esto, Hibernate falla al insertar nuevos usuarios porque
--    no incluye el campo role en el INSERT y la DB exige NOT NULL.
ALTER TABLE users ALTER COLUMN role DROP NOT NULL;

-- 4. Indice para busquedas por rol (ej: findAllByRolesContaining)
CREATE INDEX IF NOT EXISTS idx_user_roles_role ON user_roles(role);
