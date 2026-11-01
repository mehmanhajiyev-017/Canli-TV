    <!-- Компактная полупрозрачная карточка статуса (3x OK) -->
    <LinearLayout
        android:id="@+id/syncStatusCard"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_alignParentTop="true"
        android:layout_alignParentEnd="true"
        android:layout_marginTop="16dp"
        android:layout_marginEnd="16dp"
        android:orientation="vertical"
        android:paddingStart="12dp"
        android:paddingEnd="12dp"
        android:paddingTop="8dp"
        android:paddingBottom="8dp"
        android:elevation="10dp"
        android:visibility="gone">

        <TextView
            android:id="@+id/tvSyncChannels"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Каналы: 0"
            android:textColor="#FFFFFF"
            android:textSize="13sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvSyncLogos"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:text="Логотипы: 0"
            android:textColor="#B0BEC5"
            android:textSize="11sp" />

        <TextView
            android:id="@+id/tvSyncPrograms"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:text="Программы: 0"
            android:textColor="#B0BEC5"
            android:textSize="11sp" />

        <!-- Индикатор скорости потока вещания -->
        <TextView
            android:id="@+id/tvSyncSpeed"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:text="Скорость: 0 Мбит/с"
            android:textColor="#2979FF"
            android:textSize="11sp"
            android:textStyle="bold" />
    </LinearLayout>
