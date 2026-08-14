-- Ejecutar en SQL Editor después de supabase_schema.sql
-- Vistas planas para que la app lea con la API REST de Supabase.

create or replace view public.standings_view
with (security_invoker = true)
as
select
    s.id,
    s.season_year,
    s.position,
    s.points,
    s.trend,
    d.id as driver_id,
    d.first_name,
    d.last_name,
    d.code,
    d.driver_number,
    d.country,
    d.birth_date,
    d.photo_url,
    d.championships,
    d.wins,
    d.podiums,
    coalesce(t.name, 'Sin equipo') as team_name,
    coalesce(t.color_hex, '#FFFFFF') as color_hex
from public.standings s
join public.drivers d on d.id = s.driver_id
left join public.teams t on t.id = d.team_id;

create or replace view public.drivers_view
with (security_invoker = true)
as
select
    d.id,
    d.first_name,
    d.last_name,
    d.code,
    d.driver_number,
    d.country,
    d.birth_date,
    d.photo_url,
    d.championships,
    d.wins,
    d.podiums,
    d.active,
    coalesce(t.name, 'Sin equipo') as team_name,
    coalesce(t.color_hex, '#FFFFFF') as color_hex
from public.drivers d
left join public.teams t on t.id = d.team_id
where d.active = true;

grant select on public.standings_view to anon, authenticated;
grant select on public.drivers_view to anon, authenticated;

-- Imágenes locales de la app (nombres de drawable) como referencia
update public.news set image_url = 'news_featured' where title like 'Autódromo Oscar%';
update public.news set image_url = 'news_galvez' where title like 'Inaugurado en 1952%';
update public.news set image_url = 'news_motogp' where title like 'MotoGP vuelve%';
update public.news set image_url = 'news_featured' where title like 'Remodelación%';
update public.news set image_url = 'news_galvez' where title like '190 hectáreas%';
