-- V0.27__cloudsqliamuser.sql brukes til å opprette ting som finnes på GCP.
DROP ROLE IF EXISTS cloudsqliamuser;
CREATE ROLE cloudsqliamuser;


DO
$$
    BEGIN
        IF NOT EXISTS (SELECT
                       FROM pg_catalog.pg_roles
                       WHERE rolname = 'cloudsqliamserviceaccount') THEN
            CREATE ROLE cloudsqliamserviceaccount;
        END IF;
    END
$$;