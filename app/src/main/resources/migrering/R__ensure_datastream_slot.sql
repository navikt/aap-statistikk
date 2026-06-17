DO
$$
    BEGIN
        IF EXISTS(SELECT *
                  FROM pg_roles
                  WHERE rolname = 'datastream')
            AND NOT EXISTS(SELECT *
                           FROM pg_replication_slots
                           WHERE slot_name = 'ds_replication') THEN
            PERFORM PG_CREATE_LOGICAL_REPLICATION_SLOT('ds_replication', 'pgoutput');
        END IF;
    END
$$ LANGUAGE 'plpgsql';
