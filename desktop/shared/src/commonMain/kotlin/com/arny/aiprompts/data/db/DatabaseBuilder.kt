package com.arny.aiprompts.data.db

import androidx.room.RoomDatabase

expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>