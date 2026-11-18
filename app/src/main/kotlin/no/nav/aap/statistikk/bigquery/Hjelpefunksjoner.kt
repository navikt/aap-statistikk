package no.nav.aap.statistikk.bigquery

import com.google.cloud.bigquery.FieldValueList

internal fun FieldValueList.hentEllerNull(feltNavn: String): String? =
    if (!get(feltNavn).isNull) get(feltNavn).stringValue else null
