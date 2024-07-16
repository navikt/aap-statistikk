package no.nav.aap.statistikk

internal interface ISubject<E> {
    fun registerObserver(observer: IObserver<E>)
    fun removeObserver(observer: IObserver<E>)
    suspend fun notifyObservers(data: E)
}