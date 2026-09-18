// potrace JNI 封装脚手架（GPL 依赖）
// 启用步骤见 docs/BUILD.md；未启用时 VectorizeEngine 使用纯 Kotlin 轮廓追踪。
#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_omni_image_engine_NativeVectorize_trace(JNIEnv *env, jobject, jbyteArray) {
    // TODO: 集成 potrace：BMP -> potrace_trace -> SVG pathData 输出
    return env->NewStringUTF("potrace JNI not enabled");
}
