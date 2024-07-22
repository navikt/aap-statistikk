package no.nav.aap.statistikk.vilkårsresultat.service

import kotlinx.coroutines.runBlocking
import no.nav.aap.statistikk.IObserver
import no.nav.aap.statistikk.ISubject
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsResultatEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger(VilkårsResultatService::class.java)

class VilkårsResultatService(
    dataSource: DataSource
) : ISubject<Vilkårsresultat> {
    // TODO: thread safe?
    private val observers = mutableListOf<IObserver<Vilkårsresultat>>()
    private val vilkårsResultatRepository = VilkårsresultatRepository(dataSource)

    fun mottaVilkårsResultat(vilkårsresultat: Vilkårsresultat): Int {
        val id = vilkårsResultatRepository.lagreVilkårsResultat(VilkårsResultatEntity.fraDomene(vilkårsresultat))
        runBlocking { notifyObservers(vilkårsresultat) }
        return id
    }

    override fun registerObserver(observer: IObserver<Vilkårsresultat>) {
        observers.add(observer)
    }

    override fun removeObserver(observer: IObserver<Vilkårsresultat>) {
        observers.remove(observer)
    }

    override suspend fun notifyObservers(data: Vilkårsresultat) {
        logger.info("Calling observers: $observers. With new data with saksnummer ${data.saksnummer}")
        observers.forEach { observer -> observer.update(data) }
    }
}