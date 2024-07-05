package no.nav.aap.statistikk

internal interface ISubject<E> {
    fun registerObserver(observer: IObserver<E>)
    fun removeObserver(observer: IObserver<E>)
    fun notifyObservers(data: E)
}