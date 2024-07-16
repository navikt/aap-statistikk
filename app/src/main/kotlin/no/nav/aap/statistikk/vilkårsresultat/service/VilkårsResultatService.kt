package no.nav.aap.statistikk.vilkårsresultat.service

import kotlinx.coroutines.runBlocking
import no.nav.aap.statistikk.IObserver
import no.nav.aap.statistikk.ISubject
import no.nav.aap.statistikk.vilkårsresultat.Vilkårsresultat
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsResultatEntity
import no.nav.aap.statistikk.vilkårsresultat.repository.VilkårsresultatRepository
import javax.sql.DataSource

class VilkårsResultatService(
    dataSource: DataSource
) : ISubject<Vilkårsresultat> {
    // TODO: thread safe?
    private val observers = mutableListOf<IObserver<Vilkårsresultat>>()
    private val vilkårsResultatRepository = VilkårsresultatRepository(dataSource)

    fun mottaVilkårsResultat(vilkårsresultat: Vilkårsresultat) {
        vilkårsResultatRepository.lagreVilkårsResultat(VilkårsResultatEntity.fraDomene(vilkårsresultat))
        runBlocking { notifyObservers(vilkårsresultat) }
    }

    override fun registerObserver(observer: IObserver<Vilkårsresultat>) {
        observers.add(observer)
    }

    override fun removeObserver(observer: IObserver<Vilkårsresultat>) {
        observers.remove(observer)
    }

    override suspend fun notifyObservers(data: Vilkårsresultat) {
        println("Calling observers: $observers")
        observers.forEach { observer -> observer.update(data) }
    }
}