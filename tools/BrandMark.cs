
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Drawing.Text;

public class BrandMark
{
    static string[] ROWS_P = { "XXXXX..","XXXXXX.","XX...XX","XX...XX","XXXXXX.","XXXXX..","XX.....","XX.....","XX....." };
    static string[] ROWS_D = { "XXXXX..","XXXXXX.","XX...XX","XX...XX","XX...XX","XX...XX","XX...XX","XXXXXX.","XXXXX.." };
    static string[] ROWS_G = { ".XXXXX.","XXXXXXX","XX.....","XX.....","XX..XXX","XX...XX","XX...XX","XXXXXXX",".XXXXX." };
    static string[] ROWS_F = { "XXXXXXX","XXXXXXX","XX.....","XX.....","XXXXX..","XXXXX..","XX.....","XX.....","XX....." };

    public static void Run(string iconPath, int size)
    {
        using (var bmp = new Bitmap(size, size))
        {
            using (var g = Graphics.FromImage(bmp))
            {
                g.SmoothingMode = SmoothingMode.AntiAlias;
                g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
                g.Clear(Color.Transparent);

                var letters = new[] { ROWS_P, ROWS_D, ROWS_G, ROWS_F };
                var colors = new[] {
                    Color.FromArgb(30, 107, 58),
                    Color.FromArgb(181, 220, 107),
                    Color.FromArgb(242, 160, 60),
                    Color.FromArgb(99, 184, 232)
                };
                // 安全区：圆形遮罩下只保留 0.56 的内容宽度
                float blockW = size * 0.60f;
                float cell = blockW / 31f;
                float radius = cell * 0.485f;
                float ox = (size - blockW) / 2f;
                float oy = size * 0.235f;

                for (int li = 0; li < 4; li++)
                {
                    using (var brush = new SolidBrush(colors[li]))
                    using (var hi = new SolidBrush(Color.FromArgb(72, 255, 255, 255)))
                    {
                        float oxL = ox + li * 8 * cell;
                        for (int r = 0; r < 9; r++)
                        {
                            string row = letters[li][r];
                            for (int c = 0; c < row.Length; c++)
                            {
                                if (row[c] != 'X') continue;
                                float cx = oxL + c * cell + cell / 2f;
                                float cy = oy + r * cell + cell / 2f;
                                g.FillEllipse(brush, cx - radius, cy - radius, radius * 2, radius * 2);
                                float hr = radius * 0.30f;
                                g.FillEllipse(hi, cx - radius * 0.34f - hr, cy - radius * 0.36f - hr, hr * 2, hr * 2);
                            }
                        }
                    }
                }

                var fmt = new StringFormat { Alignment = StringAlignment.Center };
                using (var cnFont = new Font("Microsoft YaHei UI", size * 0.105f, FontStyle.Bold, GraphicsUnit.Pixel))
                using (var cnBrush = new SolidBrush(Color.FromArgb(20, 81, 46)))
                {
                    g.DrawString("拼豆工坊", cnFont, cnBrush,
                        new RectangleF(0, size * 0.545f, size, size * 0.14f), fmt);
                }
                using (var enFont = new Font("Segoe UI", size * 0.048f, FontStyle.Regular, GraphicsUnit.Pixel))
                using (var enBrush = new SolidBrush(Color.FromArgb(122, 143, 130)))
                {
                    g.DrawString("Pindou Studio", enFont, enBrush,
                        new RectangleF(0, size * 0.685f, size, size * 0.08f), fmt);
                }
            }
            bmp.Save(iconPath, ImageFormat.Png);
        }
    }
}
