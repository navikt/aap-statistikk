package no.nav.aap.statistikk.api

import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.route
import no.nav.aap.motor.JobbInput
import no.nav.aap.statistikk.bigquery.RekjorSakstatistikkJobb
import no.nav.aap.statistikk.db.TransactionExecutor
import no.nav.aap.statistikk.jobber.appender.JobbAppender
import java.time.LocalDate

data class RekjorJobbInput(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate
)

fun NormalOpenAPIRoute.driftApi(
    transactionExecutor: TransactionExecutor,
    jobbAppender: JobbAppender,
    rekjorSakstatistikkJobb: RekjorSakstatistikkJobb
) {
    route("/drift/rekjor") {
        post<Unit, Unit, RekjorJobbInput> { _, req ->

            transactionExecutor.withinTransaction { conn ->
                val fraOgMed = req.fraOgMed
                val tilOgMed = req.tilOgMed

                jobbAppender.leggTil(
                    conn,
                    JobbInput(rekjorSakstatistikkJobb)
                        .medParameter("fraOgMed", fraOgMed.toString())
                        .medParameter("tilOgMed", tilOgMed.toString())
                )
            }
        }
    }
}