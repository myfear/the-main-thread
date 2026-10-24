\getenv cdc_password CDC_PASSWORD
SELECT format('CREATE ROLE checkout_cdc WITH LOGIN REPLICATION PASSWORD %L', :'cdc_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'checkout_cdc') \gexec
SELECT format('ALTER ROLE checkout_cdc WITH LOGIN REPLICATION NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', :'cdc_password') \gexec
GRANT CONNECT ON DATABASE checkout TO checkout_cdc;
GRANT USAGE ON SCHEMA public TO checkout_cdc;
GRANT SELECT ON public.outbox_event TO checkout_cdc;
SELECT 'CREATE PUBLICATION checkout_outbox_pub FOR TABLE public.outbox_event WITH (publish = ''insert'')'
WHERE NOT EXISTS (SELECT FROM pg_publication WHERE pubname = 'checkout_outbox_pub') \gexec
ALTER PUBLICATION checkout_outbox_pub SET TABLE public.outbox_event;
ALTER PUBLICATION checkout_outbox_pub SET (publish = 'insert');
