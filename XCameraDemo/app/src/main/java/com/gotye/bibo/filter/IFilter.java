package com.gotye.bibo.filter;

import java.nio.FloatBuffer;

public interface IFilter {
    int getTextureTarget();

    void setTextureSize(int width, int height);

    void setSurfaceSize(int width, int height);

    void setFrameBuffer(int FrameBufferId);

    void setOutput(boolean ON);

    void setTexture(int texture1, int texture2, int texture3);

    void onDraw(float[] mvpMatrix, FloatBuffer vertexBuffer, int firstVertex, int vertexCount,
                int coordsPerVertex, int vertexStride, float[] texMatrix, FloatBuffer texBuffer,
                int textureId, int texStride);

    void releaseProgram();
}
