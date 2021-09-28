package com.example.miaow.picture.dialog

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import com.example.miaow.picture.bean.StickerAttrs
import com.example.miaow.picture.databinding.DialogPictureEditorBinding
import com.example.miaow.picture.editor.PictureEditorView
import com.example.miaow.picture.editor.layer.OnStickerClickListener
import com.example.miaow.picture.utils.ActivityCallback
import com.example.miaow.picture.utils.ActivityHelper.requestStoragePermissions
import com.example.miaow.picture.utils.ActivityHelper.startForResult
import com.example.miaow.picture.utils.AlbumUtils.getImagePath
import com.example.miaow.picture.utils.AlbumUtils.saveSystemAlbum
import com.example.miaow.picture.utils.ColorUtils
import com.example.miaow.picture.utils.PermissionsCallback

class PictureEditorDialog : PictureBaseDialog() {

    companion object {
        @JvmStatic
        fun newInstance(): PictureEditorDialog {
            return PictureEditorDialog()
        }
    }

    private var _binding: DialogPictureEditorBinding? = null
    private val binding get() = _binding!!

    private val colors: MutableList<RelativeLayout> = arrayListOf()
    private val tools: MutableList<ImageView> = arrayListOf()
    private var bitmapPath = ""
    private var callback: EditorFinishCallback? = null

    fun setBitmapPath(path: String): PictureEditorDialog {
        this.bitmapPath = path
        return this
    }

    fun setEditorFinishCallback(callback: EditorFinishCallback): PictureEditorDialog {
        this.callback = callback
        return this
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPictureEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        colors.add(binding.white)
        colors.add(binding.black)
        colors.add(binding.red)
        colors.add(binding.yellow)
        colors.add(binding.green)
        colors.add(binding.blue)
        colors.add(binding.purple)
        tools.add(binding.graffiti)
        tools.add(binding.sticker)
        tools.add(binding.text)
        tools.add(binding.mosaic)
        tools.add(binding.screenshot)
        binding.back.setOnClickListener { dismiss() }
        binding.complete.setOnClickListener {
            it.context.saveSystemAlbum(binding.picEditor.saveBitmap()) { path ->
                callback?.onFinish(path)
                dismiss()
            }
        }
        binding.picEditor.setBitmapPath(bitmapPath)
        binding.colorUndo.setOnClickListener { binding.picEditor.graffitiUndo() }
        binding.mosaicUndo.setOnClickListener { binding.picEditor.mosaicUndo() }
        colors.forEachIndexed { index, color ->
            color.setOnClickListener {
                selectedColor(color)
                binding.picEditor.setGraffitiColor(ColorUtils.colorful[index])
            }
        }
        tools.forEachIndexed { index, tool ->
            tool.setOnClickListener {
                binding.colorBar.visibility = View.GONE
                binding.mosaicUndo.visibility = View.GONE
                binding.picEditor.setMode(PictureEditorView.Mode.STICKER)
                if (!tool.isSelected) {
                    selectedTool(tool)
                    when (index) {
                        0 -> {
                            binding.colorBar.visibility = View.VISIBLE
                            binding.picEditor.setMode(PictureEditorView.Mode.GRAFFITI)
                        }
                        1 -> {
                            openAlbum()
                            tool.isSelected = false
                        }
                        2 -> {
                            openTextDialog()
                            tool.isSelected = false
                        }
                        3 -> {
                            binding.mosaicUndo.visibility = View.VISIBLE
                            binding.picEditor.setMode(PictureEditorView.Mode.MOSAIC)
                        }
                        4 -> {
                            openClipDialog(binding.picEditor.saveBitmap())
                            tool.isSelected = false
                        }
                    }
                } else {
                    tool.isSelected = false
                }
            }
        }
    }

    private fun openAlbum() {
        activity?.apply {
            requestStoragePermissions(object : PermissionsCallback {
                override fun allow() {
                    val data = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val type = "image/*"
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.setDataAndType(data, type)
                    startForResult(intent, object : ActivityCallback {
                        override fun onActivityResult(resultCode: Int, data: Intent?) {
                            data?.data?.let { uri ->
                                val bitmap = getBitmap(getImagePath(uri))
                                binding.picEditor.setSticker(StickerAttrs(bitmap))
                            }
                        }
                    })
                }

                override fun deny() {
                    val text = "当前应用缺少存储权限。\n请点击\"设置\"-\"权限\"打开所需权限。"
                    Toast.makeText(this@apply, text, Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun openTextDialog(attrs: StickerAttrs? = null) {
        PictureTextDialog.newInstance()
            .setStickerAttrs(attrs)
            .setTextFinishCallback(object : TextFinishCallback {
                override fun onFinish(attrs: StickerAttrs) {
                    binding.picEditor.setSticker(attrs, object : OnStickerClickListener {
                        override fun onClick(attrs: StickerAttrs) {
                            openTextDialog(attrs)
                        }
                    })
                }
            })
            .show(manager)
    }

    private fun openClipDialog(clipBmp: Bitmap) {
        PictureClipDialog.newInstance()
            .setBitmapResource(clipBmp)
            .setClipFinishCallback(object : ClipFinishCallback {
                override fun onFinish(path: String) {
                    callback?.onFinish(path)
                    dismiss()
                }
            })
            .show(manager)
    }

    private fun selectedColor(view: View) {
        colors.forEach {
            it.isSelected = false
        }
        view.isSelected = true
    }

    private fun selectedTool(view: View) {
        tools.forEach {
            it.isSelected = false
        }
        view.isSelected = true
    }

    private fun getBitmap(path: String): Bitmap {
        val opts = BitmapFactory.Options()
        opts.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, opts)
        opts.inJustDecodeBounds = false
        opts.inScaled = true
        opts.inDensity = opts.outWidth
        opts.inTargetDensity = 200
        return BitmapFactory.decodeFile(path, opts)
    }

}

interface EditorFinishCallback {
    fun onFinish(path: String)
}