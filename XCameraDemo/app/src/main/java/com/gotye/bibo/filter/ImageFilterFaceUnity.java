package com.gotye.bibo.filter;

import android.content.Context;
import android.os.Environment;

import com.faceunity.wrapper.faceunity;
import com.gotye.bibo.util.LogUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;

public class ImageFilterFaceUnity extends ImageFilter {

    private final static String TAG = "ImageFilterFaceUnity";
    private static final String mRootPath = Environment.getExternalStorageDirectory().
            getAbsolutePath() + "/test2/bibo";

    static final String[] m_item_names = {
            "kitty.mod",
            "fox.mod",
            "evil.mod",
            "eyeball.mod",
            "mood.mod",
            "tears.mod",
            "rabbit.mod",
            "cat.mod"};
    int m_frame_id          = 0;
    int[] m_items           = new int[1];
    int m_cur_item_id       = 0;
    int m_created_item_id   = -1;

    public ImageFilterFaceUnity(Context applicationContext) {
        this(applicationContext, 0);
    }

    public ImageFilterFaceUnity(Context applicationContext, int index) {
        super(applicationContext);

        m_cur_item_id = index;
        if (m_cur_item_id < 0)
            m_cur_item_id= 0;
        else if (m_cur_item_id > m_item_names.length - 1)
            m_cur_item_id = m_item_names.length - 1;

        try {
            File f = new File(mRootPath + "/faceUnity/v2.bin");
            InputStream is = new FileInputStream(f);
            byte[] v2data = new byte[is.available()];
            is.read(v2data);
            is.close();
            File f2 = new File(mRootPath + "/faceUnity/ar.bin");
            is = new FileInputStream(f2);
            byte[] ardata=new byte[is.available()];
            is.read(ardata);
            is.close();
            faceunity.fuInit(v2data, ardata);
        }catch (IOException e){
            LogUtil.error(TAG, "IOException: " + e);
        }
    }

    @Override
    public void onDraw(float[] mvpMatrix, FloatBuffer vertexBuffer,
                       int firstVertex, int vertexCount,
                       int coordsPerVertex, int vertexStride,
                       float[] texMatrix, FloatBuffer texBuffer,
                       int textureId, int texStride) {
        if (m_frame_id > 0 && m_frame_id % 300 == 0) {
            m_cur_item_id++;
            if (m_cur_item_id >= m_item_names.length){
                m_cur_item_id = 0;
            }
            if (m_created_item_id != m_cur_item_id && m_items[0] != 0){
                faceunity.fuDestroyItem(m_items[0]);
                m_items[0] = 0;
                m_created_item_id = m_cur_item_id;
            }
        }

        if (m_items[0] == 0) {
            LogUtil.info(TAG, "fuLoadItem");

            try {
                File f = new File(mRootPath + "/faceUnity/" + m_item_names[m_cur_item_id]);
                InputStream is = new FileInputStream(f);
                byte[] item_data=new byte[is.available()];
                is.read(item_data);
                is.close();
                m_items[0] = faceunity.fuCreateItemFromPackage(item_data);
                LogUtil.info(TAG, "fuLoadItem items[0]: " + m_items[0]);
                // parameter-setting Example
                faceunity.fuItemSetParam(m_items[0], "nose_scale", 0.5);
                faceunity.fuItemSetParam(m_items[0], "face_stretch", 1.3);
            } catch(IOException e) {
                LogUtil.error(TAG, "IOException: "+e);
            }
        }

        int newTexId = faceunity.fuRenderToTexture(
                textureId, mIncomingWidth, mIncomingHeight, m_frame_id++, m_items);

        super.onDraw(mvpMatrix, vertexBuffer,
                firstVertex, vertexCount,
                coordsPerVertex, vertexStride,
                texMatrix, texBuffer,
                newTexId, texStride);
    }

    @Override
    public void releaseProgram() {
        super.releaseProgram();

        faceunity.fuDestroyItem(m_items[0]);
        faceunity.fuDone();
    }
}
