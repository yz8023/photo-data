// resvg JNI 封装脚手架（MIT/Apache 2.0）
#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_omni_image_engine_NativeResvg_render(JNIEnv *env, jobject, jstring) {
    // TODO: 集成 resvg：SVG 字符串 -> PNG 位图（处理完整 SVG 规范）
    return env->NewStringUTF("resvg JNI not enabled");
}
