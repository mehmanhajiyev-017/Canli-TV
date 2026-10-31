<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <!-- Tam Ekran Video Oynatıcı -->
    <com.google.android.exoplayer2.ui.PlayerView
        android:id="@+id/playerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:focusable="false"
        android:clickable="true"
        app:use_controller="false" />

    <!-- Sağ Üst Senkronizasyon Durum Kartı (3x OK ile açılır) -->
    <LinearLayout
        android:id="@+id/syncStatusCard"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_alignParentTop="true"
        android:layout_alignParentEnd="true"
        android:layout_marginTop="24dp"
        android:layout_marginEnd="24dp"
        android:orientation="vertical"
        android:padding="16dp"
        android:visibility="gone">

        <TextView
            android:id="@+id/tvSyncChannels"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Kanallar: 0"
            android:textColor="#FFFFFF"
            android:textSize="16sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvSyncLogos"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:text="Logolar: 0"
            android:textColor="#B0BEC5"
            android:textSize="14sp" />

        <TextView
            android:id="@+id/tvSyncPrograms"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:text="Programlar: 0"
            android:textColor="#B0BEC5"
            android:textSize="14sp" />
    </LinearLayout>

    <!-- Sol Kategori Paneli -->
    <LinearLayout
        android:id="@+id/categoryPanel"
        android:layout_width="320dp"
        android:layout_height="match_parent"
        android:layout_alignParentStart="true"
        android:background="#E60B0E14"
        android:orientation="vertical"
        android:padding="16dp"
        android:visibility="gone">

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="12dp"
            android:text="Kategoriler"
            android:textColor="#FFFFFF"
            android:textSize="20sp"
            android:textStyle="bold" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/recyclerViewCategories"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1" />

        <LinearLayout
            android:id="@+id/btnOpenSettings"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:layout_marginTop="10dp"
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
                android:textColor="#B0BEC5"
                android:textSize="20sp" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="12dp"
                android:text="Ayarlar"
                android:textColor="#ECEFF1"
                android:textSize="16sp"
                android:textStyle="bold" />
        </LinearLayout>
    </LinearLayout>

    <!-- Sol Kanal Listesi Paneli -->
    <LinearLayout
        android:id="@+id/channelPanel"
        android:layout_width="420dp"
        android:layout_height="match_parent"
        android:layout_alignParentStart="true"
        android:background="#F00B0E14"
        android:orientation="vertical"
        android:padding="12dp"
        android:visibility="gone">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/recyclerViewChannels"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
    </LinearLayout>

    <!-- Alt Bilgi Bandı (Info Banner) -->
    <LinearLayout
        android:id="@+id/infoBanner"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_alignParentBottom="true"
        android:layout_margin="24dp"
        android:background="#D90B0E14"
        android:orientation="vertical"
        android:padding="18dp"
        android:visibility="gone">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/tvBannerNumber"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginEnd="16dp"
                android:text="1"
                android:textColor="#2979FF"
                android:textSize="28sp"
                android:textStyle="bold" />

            <ImageView
                android:id="@+id/imgBannerLogo"
                android:layout_width="44dp"
                android:layout_height="44dp"
                android:layout_marginEnd="16dp"
                android:scaleType="fitCenter"
                android:visibility="gone" />

            <TextView
                android:id="@+id/tvBannerName"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Kanal İsmi"
                android:textColor="#FFFFFF"
                android:textSize="22sp"
                android:textStyle="bold" />
        </LinearLayout>

        <TextView
            android:id="@+id/tvBannerCategory"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:text="Kategori"
            android:textColor="#B0BEC5"
            android:textSize="16sp" />
    </LinearLayout>

    <!-- Sağ Sayı Tuş Takımı Paneli -->
    <LinearLayout
        android:id="@+id/numPadPanel"
        android:layout_width="260dp"
        android:layout_height="wrap_content"
        android:layout_alignParentEnd="true"
        android:layout_centerVertical="true"
        android:layout_marginEnd="24dp"
        android:orientation="vertical"
        android:padding="16dp"
        android:visibility="gone">

        <TextView
            android:id="@+id/tvNumpadDisplay"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="12dp"
            android:gravity="center"
            android:text="---"
            android:textColor="#FFFFFF"
            android:textSize="26sp"
            android:textStyle="bold" />

        <GridLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:columnCount="3"
            android:rowCount="4">

            <Button android:id="@+id/btn1" style="?android:attr/borderlessButtonStyle" android:layout_width="0dp" android:layout_height="52dp" android:layout_columnWeight="1" android:layout_margin="3dp" android:text="1" android:textSize="18sp" />
            <Button android:id="@+id/btn2" style="?android:attr/borderlessButtonStyle" android:layout_width="0dp" android:layout_height="52dp" android:layout_columnWeight="1" android:layout_margin="3dp" android:text="2" android:textSize="18sp" />
            <Button android:id="@+id/btn3" style="?android:attr/borderlessButtonStyle" android:layout_width="0dp" android:layout_height="52dp" android:layout_columnWeight="1" android:layout_margin="3dp" android:text="3" android:textSize="18sp" />

            <Button android:id="@+id/btn4" style="?android:attr/borderlessButtonStyle" android:layout_width="0dp" android:layout_height="52dp" android:layout_columnWeight="1" android:layout_margin="3dp" android:text="4" android:textSize="18sp" />
            <Button android:id="@+id/btn5" style="?android:attr/borderlessButtonStyle" android:layout_width="0dp" android:layout_height="52dp" android:layout_columnWeight="1" android:layout_margin="3dp" android:text="5" android:textSize="18sp" />
            <Button android:id="@+id/btn6" style="?android:attr/borderlessButtonStyle" android:layout_width="0dp" android:layout_height="52dp" android:layout_columnWeight="1" android:layout_margin="3dp" android:text="6" android:textSize="18sp" />

            <Button android:id="@+id/btn7" style="?android:attr/borderlessButtonStyle" android:layout_width="0dp" android:layout_height="52dp" android:layout_columnWeight="1" android:layout_margin="3dp" android:text="7" android:textSize="18sp" />
            <Button android:id="@+id/btn8" style="?android:attr/borderlessButtonStyle" android:layout_width="0dp" android:layout_height="52dp" android:layout_columnWeight="1" android:layout_margin="3dp" android:text="8" android:textSize="18sp" />
            <Button android:id="@+id/btn9" style="?android:attr/borderlessButtonStyle" android:layout_width="0dp" android:layout_height="52dp" android:layout_columnWeight="1" android:layout_margin="3dp" android:text="9" android:textSize="18sp" />

            <Button android:id="@+id/btnClear" style="?android:attr/borderlessButtonStyle" android:layout_width="0dp" android:layout_height="52dp" android:layout_columnWeight="1" android:layout_margin="3dp" android:text="C" android:textColor="#FF5252" android:textSize="18sp" />
            <Button android:id="@+id/btn0" style="?android:attr/borderlessButtonStyle" android:layout_width="0dp" android:layout_height="52dp" android:layout_columnWeight="1" android:layout_margin="3dp" android:text="0" android:textSize="18sp" />
            <Button android:id="@+id/btnGo" style="?android:attr/borderlessButtonStyle" android:layout_width="0dp" android:layout_height="52dp" android:layout_columnWeight="1" android:layout_margin="3dp" android:text="OK" android:textColor="#4CAF50" android:textSize="18sp" />
        </GridLayout>
    </LinearLayout>

    <!-- Ayarlar Diyaloğu (M3U / EPG / Mod Değiştirici) -->
    <RelativeLayout
        android:id="@+id/linkDialogPanel"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#D9000000"
        android:clickable="true"
        android:focusable="true"
        android:gravity="center"
        android:visibility="gone">

        <LinearLayout
            android:layout_width="650dp"
            android:layout_height="wrap_content"
            android:background="#1B222E"
            android:orientation="vertical"
            android:padding="24dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:text="Uygulama Ayarları"
                android:textColor="#FFFFFF"
                android:textSize="22sp"
                android:textStyle="bold" />

            <!-- M3U Alanı -->
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="1. M3U Playlist Bağlantısı:"
                android:textColor="#B0BEC5"
                android:textSize="14sp" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:layout_marginBottom="14dp"
                android:orientation="horizontal">

                <EditText
                    android:id="@+id/etM3uUrl"
                    android:layout_width="0dp"
                    android:layout_height="50dp"
                    android:layout_weight="1"
                    android:hint="http://.../playlist.m3u"
                    android:paddingStart="12dp"
                    android:paddingEnd="12dp"
                    android:singleLine="true"
                    android:textSize="14sp" />

                <Button
                    android:id="@+id/btnSaveM3u"
                    android:layout_width="64dp"
                    android:layout_height="50dp"
                    android:layout_marginStart="8dp"
                    android:text="KAYDET"
                    android:textSize="12sp"
                    android:textStyle="bold" />

                <Button
                    android:id="@+id/btnRefreshM3u"
                    android:layout_width="50dp"
                    android:layout_height="50dp"
                    android:layout_marginStart="6dp"
                    android:text="↻"
                    android:textSize="18sp" />
            </LinearLayout>

            <!-- EPG Alanı -->
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="2. EPG (Program Rehberi) Bağlantısı (.xml / .gz):"
                android:textColor="#B0BEC5"
                android:textSize="14sp" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:layout_marginBottom="16dp"
                android:orientation="horizontal">

                <EditText
                    android:id="@+id/etEpgUrl"
                    android:layout_width="0dp"
                    android:layout_height="50dp"
                    android:layout_weight="1"
                    android:hint="http://.../epg.xml.gz"
                    android:paddingStart="12dp"
                    android:paddingEnd="12dp"
                    android:singleLine="true"
                    android:textSize="14sp" />

                <Button
                    android:id="@+id/btnSaveEpg"
                    android:layout_width="64dp"
                    android:layout_height="50dp"
                    android:layout_marginStart="8dp"
                    android:text="KAYDET"
                    android:textSize="12sp"
                    android:textStyle="bold" />

                <Button
                    android:id="@+id/btnRefreshEpg"
                    android:layout_width="50dp"
                    android:layout_height="50dp"
                    android:layout_marginStart="6dp"
                    android:text="↻"
                    android:textSize="18sp" />
            </LinearLayout>

            <!-- Alt Butonlar: TV/Mobil Geçiş Karesi ve Kapat Butonu -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:gravity="end|center_vertical"
                android:orientation="horizontal">

                <Button
                    android:id="@+id/btnToggleMode"
                    android:layout_width="56dp"
                    android:layout_height="52dp"
                    android:layout_marginEnd="12dp"
                    android:clickable="true"
                    android:focusable="true"
                    android:text="📺"
                    android:textSize="20sp" />

                <Button
                    android:id="@+id/btnCloseSettings"
                    android:layout_width="wrap_content"
                    android:layout_height="52dp"
                    android:clickable="true"
                    android:focusable="true"
                    android:paddingStart="24dp"
                    android:paddingEnd="24dp"
                    android:text="KAPAT"
                    android:textColor="#ECEFF1"
                    android:textSize="15sp"
                    android:textStyle="bold" />
            </LinearLayout>
        </LinearLayout>
    </RelativeLayout>

</RelativeLayout>
