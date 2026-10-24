<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <!-- Əsas videopleyer -->
    <com.google.android.exoplayer2.ui.PlayerView
        android:id="@+id/playerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:resize_mode="fill"
        app:use_controller="false" />

    <!-- Kateqoriyalar paneli (Sol) -->
    <LinearLayout
        android:id="@+id/categoryPanel"
        android:layout_width="290dp"
        android:layout_height="match_parent"
        android:layout_gravity="start"
        android:background="#E610131A"
        android:orientation="vertical"
        android:padding="16dp"
        android:visibility="gone">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            android:text="Категории"
            android:textColor="#FFFFFF"
            android:textSize="20sp"
            android:textStyle="bold" />

        <!-- Kateqoriyalar siyahısı (Üst hissə) -->
        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/recyclerViewCategories"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1" />

        <!-- "Настройки" - Həmişə ən aşağıda -->
        <LinearLayout
            android:id="@+id/btnOpenSettings"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:layout_marginTop="10dp"
            android:background="#1C222E"
            android:clickable="true"
            android:focusable="true"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:paddingStart="16dp"
            android:paddingEnd="16dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="⚙"
                android:textColor="#90A4AE"
                android:textSize="18sp" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="14dp"
                android:text="Настройки"
                android:textColor="#CFD8DC"
                android:textSize="15sp"
                android:textStyle="bold" />
        </LinearLayout>
    </LinearLayout>

    <!-- Kanallar paneli -->
    <LinearLayout
        android:id="@+id/channelPanel"
        android:layout_width="380dp"
        android:layout_height="match_parent"
        android:layout_gravity="start"
        android:background="#E610131A"
        android:orientation="vertical"
        android:padding="12dp"
        android:visibility="gone">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/recyclerViewChannels"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
    </LinearLayout>

    <!-- Rəqəmli axtarış paneli (pultda rəqəm sıxıldıqda çıxır) -->
    <LinearLayout
        android:id="@+id/numPadPanel"
        android:layout_width="280dp"
        android:layout_height="wrap_content"
        android:layout_gravity="end|center_vertical"
        android:layout_marginEnd="32dp"
        android:background="#F0181B22"
        android:gravity="center"
        android:orientation="vertical"
        android:padding="16dp"
        android:visibility="gone">

        <TextView
            android:id="@+id/tvNumpadDisplay"
            android:layout_width="match_parent"
            android:layout_height="50dp"
            android:layout_marginBottom="16dp"
            android:background="#1E232B"
            android:gravity="center"
            android:text="---"
            android:textColor="#2979FF"
            android:textSize="26sp"
            android:textStyle="bold" />

        <GridLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:columnCount="3">

            <Button android:id="@+id/btn1" android:layout_width="65dp" android:layout_height="50dp" android:text="1" />
            <Button android:id="@+id/btn2" android:layout_width="65dp" android:layout_height="50dp" android:text="2" />
            <Button android:id="@+id/btn3" android:layout_width="65dp" android:layout_height="50dp" android:text="3" />

            <Button android:id="@+id/btn4" android:layout_width="65dp" android:layout_height="50dp" android:text="4" />
            <Button android:id="@+id/btn5" android:layout_width="65dp" android:layout_height="50dp" android:text="5" />
            <Button android:id="@+id/btn6" android:layout_width="65dp" android:layout_height="50dp" android:text="6" />

            <Button android:id="@+id/btn7" android:layout_width="65dp" android:layout_height="50dp" android:text="7" />
            <Button android:id="@+id/btn8" android:layout_width="65dp" android:layout_height="50dp" android:text="8" />
            <Button android:id="@+id/btn9" android:layout_width="65dp" android:layout_height="50dp" android:text="9" />

            <Button android:id="@+id/btnClear" android:layout_width="65dp" android:layout_height="50dp" android:text="C" />
            <Button android:id="@+id/btn0" android:layout_width="65dp" android:layout_height="50dp" android:text="0" />
            <Button android:id="@+id/btnGo" android:layout_width="65dp" android:layout_height="50dp" android:text="OK" />
        </GridLayout>
    </LinearLayout>

    <!-- İnformasiya banneri -->
    <LinearLayout
        android:id="@+id/infoBanner"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:background="#CC10131A"
        android:orientation="vertical"
        android:paddingStart="32dp"
        android:paddingTop="16dp"
        android:paddingEnd="32dp"
        android:paddingBottom="24dp"
        android:visibility="gone">

        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/tvBannerNumber"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="#2979FF"
                android:paddingStart="8dp"
                android:paddingTop="2dp"
                android:paddingEnd="8dp"
                android:paddingBottom="2dp"
                android:text="1"
                android:textColor="#FFFFFF"
                android:textSize="18sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tvBannerName"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="14dp"
                android:text="Kanal adı"
                android:textColor="#FFFFFF"
                android:textSize="22sp"
                android:textStyle="bold" />
        </LinearLayout>

        <TextView
            android:id="@+id/tvBannerCategory"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:text="● Qrup"
            android:textColor="#78909C"
            android:textSize="14sp" />
    </LinearLayout>

    <!-- Ayarlar dialoqu -->
    <LinearLayout
        android:id="@+id/linkDialogPanel"
        android:layout_width="480dp"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:background="#F01E232B"
        android:orientation="vertical"
        android:padding="24dp"
        android:visibility="gone">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            android:text="Настройки плейлиста"
            android:textColor="#FFFFFF"
            android:textSize="20sp"
            android:textStyle="bold" />

        <EditText
            android:id="@+id/etM3uUrl"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="12dp"
            android:hint="Ссылка на M3U плейлист"
            android:singleLine="true"
            android:textColor="#FFFFFF"
            android:textColorHint="#78909C" />

        <EditText
            android:id="@+id/etEpgUrl"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="20dp"
            android:hint="Ссылка на EPG (необязательно)"
            android:singleLine="true"
            android:textColor="#FFFFFF"
            android:textColorHint="#78909C" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="end"
            android:orientation="horizontal">

            <Button
                android:id="@+id/btnCloseSettings"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginEnd="12dp"
                android:text="Отмена" />

            <Button
                android:id="@+id/btnSaveUrl"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Сохранить" />
        </LinearLayout>
    </LinearLayout>

</FrameLayout>
