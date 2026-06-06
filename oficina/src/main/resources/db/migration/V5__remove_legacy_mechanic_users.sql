-- ===================================================
-- V5: Remove o perfil legado MECHANIC.
--     O mecânico não acessa mais o sistema (apenas dono
--     ADMIN e atendente ATTENDANT). Limpa usuários antigos
--     semeados como MECHANIC (ex.: 'mecanico') para que
--     não consigam mais autenticar.
-- ===================================================

DELETE FROM app_users WHERE role = 'MECHANIC';
