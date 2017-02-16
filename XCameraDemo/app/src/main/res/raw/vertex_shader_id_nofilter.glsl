uniform mat4 uMVPMatrix;  // MVP 的变换矩阵（整体变形）
uniform mat4 uTexMatrix; // 纹理的变换矩阵

attribute vec4 a_Position;
attribute vec4 a_TexCoord;

varying vec2 v_TexCoord;

void main()
{
	gl_Position = uMVPMatrix * a_Position;
	//v_TexCoord = a_TexCoord;
	v_TexCoord = (uTexMatrix * a_TexCoord).xy;
}