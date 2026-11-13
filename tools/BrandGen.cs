
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Drawing.Text;

public class BrandGen
{
    static string[] ROWS_P = { "XXXXX..","XXXXXX.","XX...XX","XX...XX","XXXXXX.","XXXXX..","XX.....","XX.....","XX....." };
    static string[] ROWS_D = { "XXXXX..","XXXXXX.","XX...XX","XX...XX","XX...XX","XX...XX","XX...XX","XXXXXX.","XXXXX.." };
    static string[] ROWS_G = { ".XXXXX.","XXXXXXX","XX.....","XX.....","XX..XXX","XX...XX","XX...XX","XXXXXXX",".XXXXX." };
    static string[] ROWS_F = { "XXXXXXX","XXXXXXX","XX.....","XX.....","XXXXX..","XXXXX..","XX.....","XX.....","XX....." };

    static Bitmap BuildLogo(int size)
    {
        var bmp = new Bitmap(size, size);
        using (var g = Graphics.FromImage(bmp))
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
            g.Clear(Color.FromArgb(255, 255, 252, 244));

            Blob(g, 120, 110, 240, Color.FromArgb(150, 205, 165), 130);
            Blob(g, 690, 90, 220, Color.FromArgb(250, 205, 160), 120);
            Blob(g, 720, 660, 240, Color.FromArgb(160, 205, 240), 120);
            Blob(g, 100, 700, 220, Color.FromArgb(190, 225, 175), 115);
            Blob(g, 400, 380, 330, Color.FromArgb(235, 245, 225), 70);

            var letters = new[] { ROWS_P, ROWS_D, ROWS_G, ROWS_F };
            var colors = new[] {
                Color.FromArgb(30, 107, 58),
                Color.FromArgb(181, 220, 107),
                Color.FromArgb(242, 160, 60),
                Color.FromArgb(99, 184, 232)
            };
            int cols = 31, rows = 9;
            float blockW = size * 0.775f;
            float cell = blockW / cols;
            float radius = cell * 0.485f;
            float ox = (size - blockW) / 2f;
            float oy = size * 0.256f;

            for (int li = 0; li < 4; li++)
            {
                using (var brush = new SolidBrush(colors[li]))
                using (var hi = new SolidBrush(Color.FromArgb(72, 255, 255, 255)))
                {
                    float oxL = ox + li * 8 * cell;
                    for (int r = 0; r < rows; r++)
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
            using (var cnFont = new Font("Microsoft YaHei UI", size * 0.098f, FontStyle.Bold, GraphicsUnit.Pixel))
            using (var cnBrush = new SolidBrush(Color.FromArgb(20, 81, 46)))
            {
                g.DrawString("拼豆工坊", cnFont, cnBrush,
                    new RectangleF(0, size * 0.588f, size, size * 0.15f), fmt);
            }
            using (var enFont = new Font("Segoe UI", size * 0.043f, FontStyle.Regular, GraphicsUnit.Pixel))
            using (var enBrush = new SolidBrush(Color.FromArgb(122, 143, 130)))
            {
                g.DrawString("Pindou Studio", enFont, enBrush,
                    new RectangleF(0, size * 0.741f, size, size * 0.08f), fmt);
            }
        }
        return bmp;
    }

    static void Blob(Graphics g, float cx, float cy, float r, Color col, int alpha)
    {
        using (var path = new GraphicsPath())
        {
            path.AddEllipse(cx - r, cy - r, r * 2, r * 2);
            using (var brush = new PathGradientBrush(path))
            {
                brush.CenterColor = Color.FromArgb(alpha, col);
                brush.SurroundColors = new[] { Color.FromArgb(0, col) };
                brush.CenterPoint = new PointF(cx, cy);
                g.FillPath(brush, path);
            }
        }
    }

    public static void Run(string logoPath, string iconPath)
    {
        using (var logo = BuildLogo(800))
        {
            logo.Save(logoPath, ImageFormat.Png);

            int n = 432;
            using (var icon = new Bitmap(n, n))
            {
                using (var g = Graphics.FromImage(icon))
                {
                    g.SmoothingMode = SmoothingMode.HighQuality;
                    g.InterpolationMode = InterpolationMode.HighQualityBicubic;
                    g.Clear(Color.Transparent);
                    float side = n * 0.685f;
                    float off = (n - side) / 2f;
                    g.DrawImage(logo, off, off, side, side);
                }
                icon.Save(iconPath, ImageFormat.Png);
            }
        }
    }
}
