CREATE INDEX IDX_BEHANDLING_HISTORIKK_BEHANDLING_ID ON behandling_historikk (behandling_id);

CREATE INDEX IDX_BEHANDLING_HISTORIKK_SLETTET ON behandling_historikk (slettet);

CREATE INDEX IDX_BEHANDLING_VERSJON_ID ON behandling_historikk (versjon_id);
