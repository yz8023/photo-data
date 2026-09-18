// libwebp JNI 封装脚手架（BSD，Google）
#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_omni_image_engine_NativeWebp_encode(JNIEnv *env, jobject, jbyteArray) {
    // TODO: 集成 libwebp：将 RGBA 像素编码为 WebP（质量可调）
    return env->NewStringUTF("webp JNI not enabled");
}
