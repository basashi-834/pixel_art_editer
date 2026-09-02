using Avalonia.Controls;
using Avalonia.Interactivity;
using PixelSpriteEditor.Services;

namespace PixelSpriteEditor.Views;

/// <summary>
/// Lets the user specify a new canvas either directly in pixels, or in
/// 16x16 fighting-game-style tiles (converted to pixels before creating the
/// canvas -- PixelCanvas itself only ever deals in raw pixel dimensions).
/// </summary>
public partial class NewCanvasDialog : Window
{
    public (int Width, int Height)? Result { get; private set; }

    public NewCanvasDialog()
    {
        InitializeComponent();

        ModePixelsRadio.IsCheckedChanged += (_, _) => UpdateModeUi();
        ModeTilesRadio.IsCheckedChanged += (_, _) => UpdateModeUi();
        WidthInput.ValueChanged += (_, _) => UpdatePreview();
        HeightInput.ValueChanged += (_, _) => UpdatePreview();
        OkBtn.Click += OnOk;
        CancelBtn.Click += (_, _) => Close();
        Loc.Changed += RefreshTexts;
        Closed += (_, _) => Loc.Changed -= RefreshTexts;

        RefreshTexts();
        UpdateModeUi();
    }

    private bool IsTileMode => ModeTilesRadio.IsChecked == true;

    private void UpdateModeUi()
    {
        if (IsTileMode)
        {
            WidthInput.Maximum = 64;
            HeightInput.Maximum = 64;
            if (WidthInput.Value > 64) WidthInput.Value = 4;
            if (HeightInput.Value > 64) HeightInput.Value = 4;
        }
        else
        {
            WidthInput.Maximum = 1024;
            HeightInput.Maximum = 1024;
            if (WidthInput.Value is < 4) WidthInput.Value = 64;
        }
        UpdatePreview();
    }

    private void UpdatePreview()
    {
        int w = (int)(WidthInput.Value ?? 1);
        int h = (int)(HeightInput.Value ?? 1);
        if (IsTileMode) { w *= 16; h *= 16; }
        PreviewText.Text = Loc.T($"= {w} x {h} px", $"= {w} x {h} ピクセル");
    }

    private void OnOk(object? sender, RoutedEventArgs e)
    {
        int w = (int)(WidthInput.Value ?? 1);
        int h = (int)(HeightInput.Value ?? 1);
        if (IsTileMode) { w *= 16; h *= 16; }
        Result = (Math.Max(1, w), Math.Max(1, h));
        Close();
    }

    private void RefreshTexts()
    {
        Title = Loc.T("New Canvas", "新規キャンバス");
        TitleText.Text = Title;
        ModePixelsRadio.Content = Loc.T("By pixel size", "ピクセルサイズで指定");
        ModeTilesRadio.Content = Loc.T("By tile count (16x16 blocks)", "タイル数で指定 (16x16ブロック)");
        WidthLabel.Text = Loc.T("Width", "幅");
        HeightLabel.Text = Loc.T("Height", "高さ");
        OkBtn.Content = Loc.T("OK", "OK");
        CancelBtn.Content = Loc.T("Cancel", "キャンセル");
        UpdatePreview();
    }
}
