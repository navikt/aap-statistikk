package no.nav.aap.statistikk.bigquery

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import no.nav.aap.statistikk.IObserver
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import java.util.concurrent.Executors

class BigQueryObserver(
    private val bqRepository: BQRepository,
    private val dispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
) : IObserver<Vilkårsresultat> {
    private val channel = Channel<Vilkårsresultat>()

    init {
        CoroutineScope(dispatcher).launch {
            for (item in channel) {
                save(item)
            }
        }
    }

    override suspend fun update(data: Vilkårsresultat) {
        channel.send(data)
    }

    private fun save(data: Vilkårsresultat) {
        bqRepository.lagreVilkårsResultat(vilkårsresultat = data)
    }

    override fun toString(): String {
        return "BigQueryObserver(bqRepository=$bqRepository, dispatcher=$dispatcher, channel=$channel)"
    }
}