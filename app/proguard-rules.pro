# InputMethodService ve Klavye Servisini koru
-keep public class com.example.comexamplet9keyboard.T9Service {
    *;
}

# Özel View sınıflarını (IconView, JoystickView) koru
-keep public class com.example.comexamplet9keyboard.IconView {
    *;
}
-keep public class com.example.comexamplet9keyboard.JoystickView {
    *;
}

# Pano ve Sözlük Activity'lerini koru
-keep public class com.example.comexamplet9keyboard.ClipEditActivity {
    *;
}
-keep public class com.example.comexamplet9keyboard.UserDictionaryActivity {
    *;
}

# Veri modelleri ve yardımcı store sınıflarını koru
-keep class com.example.comexamplet9keyboard.UserWord {
    *;
}
-keep class com.example.comexamplet9keyboard.ClipStore {
    *;
}