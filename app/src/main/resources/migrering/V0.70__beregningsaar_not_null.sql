alter table grunnlag
    alter column beregningsaar set not null;

alter table grunnlag_11_19
    alter column inntekter_foregaaende_aar set not null;

alter table grunnlag_ufore
    alter column ufore_inntekter set not null;