package space.liushenme.markdownreader.di

import javax.inject.Qualifier

/** Process-lifetime scope for work that must outlive an Activity/ViewModel. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
