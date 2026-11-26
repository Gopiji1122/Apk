import 'dart:io';
import 'dart:typed_data';
import 'package:archive/archive.dart';
import 'package:cunning_document_scanner/cunning_document_scanner.dart';
import 'package:excel/excel.dart' as xl;
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'package:image/image.dart' as img;
import 'package:path_provider/path_provider.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:pdfrx/pdfrx.dart';
import 'package:share_plus/share_plus.dart';
import 'package:syncfusion_flutter_pdf/pdf.dart' as sf;
import 'package:xml/xml.dart';

void main() => runApp(const App());

ThemeData _theme(Brightness b) => ThemeData(
    useMaterial3: true, colorSchemeSeed: const Color(0xFF5B5BF0), brightness: b);

class App extends StatelessWidget {
  const App({super.key});
  @override
  Widget build(BuildContext c) => MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'PDF Forge',
      theme: _theme(Brightness.light),
      darkTheme: _theme(Brightness.dark),
      home: const Shell());
}

// ---------- helpers ----------
Future<Directory> store() async {
  final d = Directory('${(await getApplicationDocumentsDirectory()).path}/pdfs');
  if (!await d.exists()) await d.create(recursive: true);
  return d;
}

Future<String> savePdf(List<int> bytes, String name) async {
  final f = File('${(await store()).path}/${name}_${DateTime.now().millisecondsSinceEpoch}.pdf');
  await f.writeAsBytes(bytes);
  return f.path;
}

Future<List<String>> pick(List<String> ext, {bool multi = false}) async {
  final r = await FilePicker.platform
      .pickFiles(allowMultiple: multi, type: FileType.custom, allowedExtensions: ext);
  return r?.paths.whereType<String>().toList() ?? [];
}

void openPdf(BuildContext c, String p) =>
    Navigator.push(c, MaterialPageRoute(builder: (_) => Viewer(p)));

void toast(BuildContext c, String m) => ScaffoldMessenger.of(c)
    .showSnackBar(SnackBar(content: Text(m), behavior: SnackBarBehavior.floating));

Future<String?> ask(BuildContext c, String title, {bool obscure = false}) {
  final t = TextEditingController();
  return showDialog<String>(
      context: c,
      builder: (_) => AlertDialog(
            title: Text(title),
            content: TextField(controller: t, obscureText: obscure, autofocus: true),
            actions: [
              TextButton(onPressed: () => Navigator.pop(c), child: const Text('Cancel')),
              FilledButton(onPressed: () => Navigator.pop(c, t.text), child: const Text('OK')),
            ],
          ));
}

Uint8List applyFilter(String path, int f) {
  var im = img.decodeImage(File(path).readAsBytesSync())!;
  if (im.width > 2000) im = img.copyResize(im, width: 2000);
  if (f == 1) im = img.grayscale(im);
  if (f == 2) im = img.adjustColor(img.grayscale(im), contrast: 1.8, brightness: 1.08);
  if (f == 3) im = img.adjustColor(im, saturation: 1.4, contrast: 1.15);
  return img.encodeJpg(im, quality: 85);
}

Future<List<int>> imagesToPdf(List<Uint8List> imgs) async {
  final d = pw.Document();
  for (final b in imgs) {
    final m = pw.MemoryImage(b);
    d.addPage(pw.Page(
        pageFormat: PdfPageFormat.a4,
        margin: pw.EdgeInsets.zero,
        build: (_) => pw.Center(child: pw.Image(m, fit: pw.BoxFit.contain))));
  }
  return d.save();
}

Future<String> ocr(String imagePath) async {
  final r = TextRecognizer(script: TextRecognitionScript.latin);
  final t = await r.processImage(InputImage.fromFilePath(imagePath));
  await r.close();
  return t.text;
}

void showText(BuildContext c, String text) => showModalBottomSheet(
    context: c,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (_) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: .7,
        builder: (_, sc) => Column(children: [
              Expanded(
                  child: SingleChildScrollView(
                      controller: sc,
                      padding: const EdgeInsets.all(16),
                      child: SelectableText(text.isEmpty ? 'No text found' : text))),
              Padding(
                  padding: const EdgeInsets.all(12),
                  child: Row(children: [
                    Expanded(
                        child: OutlinedButton.icon(
                            onPressed: () {
                              Clipboard.setData(ClipboardData(text: text));
                              toast(c, 'Copied');
                            },
                            icon: const Icon(Icons.copy),
                            label: const Text('Copy'))),
                    const SizedBox(width: 12),
                    Expanded(
                        child: FilledButton.icon(
                            onPressed: () => Share.share(text),
                            icon: const Icon(Icons.share),
                            label: const Text('Share'))),
                  ]))
            ])));

// ---------- tools ----------
class Tool {
  final IconData icon;
  final String title, sub;
  final Color color;
  final Future<void> Function(BuildContext) run;
  Tool(this.icon, this.title, this.sub, this.color, this.run);
}

Future<void> finish(BuildContext c, List<int> bytes, String name) async {
  final p = await savePdf(bytes, name);
  if (c.mounted) openPdf(c, p);
}

final tools = <Tool>[
  Tool(Icons.merge_type, 'Merge', 'Combine PDFs', const Color(0xFF5B5BF0), (c) async {
    final ps = await pick(['pdf'], multi: true);
    if (ps.length < 2) return c.mounted ? toast(c, 'Pick at least 2 PDFs') : null;
    final dest = sf.PdfDocument(inputBytes: File(ps[0]).readAsBytesSync());
    sf.PdfDocumentBase.merge(dest, ps.skip(1).map((p) => File(p).readAsBytesSync()).toList());
    final out = await dest.save();
    dest.dispose();
    if (c.mounted) await finish(c, out, 'merged');
  }),
  Tool(Icons.view_agenda_outlined, 'Organize', 'Reorder, rotate, delete', const Color(0xFF10B981),
      (c) async {
    final ps = await pick(['pdf']);
    if (ps.isNotEmpty && c.mounted) {
      Navigator.push(c, MaterialPageRoute(builder: (_) => Organize(ps.first)));
    }
  }),
  Tool(Icons.lock_outline, 'Protect', 'Password encrypt', const Color(0xFFF59E0B), (c) async {
    final ps = await pick(['pdf']);
    if (ps.isEmpty || !c.mounted) return;
    final pw1 = await ask(c, 'Set password', obscure: true);
    if (pw1 == null || pw1.isEmpty) return;
    final d = sf.PdfDocument(inputBytes: File(ps.first).readAsBytesSync());
    d.security.userPassword = pw1;
    final out = await d.save();
    d.dispose();
    if (c.mounted) await finish(c, out, 'protected');
  }),
  Tool(Icons.water_drop_outlined, 'Watermark', 'Stamp text on pages', const Color(0xFF06B6D4),
      (c) async {
    final ps = await pick(['pdf']);
    if (ps.isEmpty || !c.mounted) return;
    final txt = await ask(c, 'Watermark text');
    if (txt == null || txt.isEmpty) return;
    final d = sf.PdfDocument(inputBytes: File(ps.first).readAsBytesSync());
    final font = sf.PdfStandardFont(sf.PdfFontFamily.helvetica, 56, style: sf.PdfFontStyle.bold);
    for (var i = 0; i < d.pages.count; i++) {
      final p = d.pages[i];
      final g = p.graphics;
      g.save();
      g.setTransparency(.22);
      g.translateTransform(p.size.width / 2, p.size.height / 2);
      g.rotateTransform(-40);
      g.drawString(txt, font,
          brush: sf.PdfBrushes.gray,
          bounds: Rect.fromLTWH(-p.size.width / 2, -30, p.size.width, 70),
          format: sf.PdfStringFormat(alignment: sf.PdfTextAlignment.center));
      g.restore();
    }
    final out = await d.save();
    d.dispose();
    if (c.mounted) await finish(c, out, 'watermarked');
  }),
  Tool(Icons.image_outlined, 'Images → PDF', 'From gallery', const Color(0xFFEC4899), (c) async {
    final r = await FilePicker.platform.pickFiles(allowMultiple: true, type: FileType.image);
    final ps = r?.paths.whereType<String>().toList() ?? [];
    if (ps.isEmpty) return;
    final out = await imagesToPdf(ps.map((p) => applyFilter(p, 0)).toList());
    if (c.mounted) await finish(c, out, 'images');
  }),
  Tool(Icons.description_outlined, 'Word/Excel → PDF', 'DOCX, XLSX offline', const Color(0xFF3B82F6),
      (c) async {
    final ps = await pick(['docx', 'xlsx']);
    if (ps.isEmpty) return;
    final doc = pw.Document();
    if (ps.first.endsWith('.docx')) {
      final z = ZipDecoder().decodeBytes(File(ps.first).readAsBytesSync());
      final x = XmlDocument.parse(
          String.fromCharCodes(z.findFile('word/document.xml')!.content as List<int>));
      final paras = x.findAllElements('w:p').map((p) => p.findAllElements('w:t').map((t) => t.innerText).join()).toList();
      doc.addPage(pw.MultiPage(
          margin: const pw.EdgeInsets.all(40),
          build: (_) => paras.map((t) => pw.Padding(padding: const pw.EdgeInsets.only(bottom: 6), child: pw.Text(t))).toList()));
    } else {
      final ex = xl.Excel.decodeBytes(File(ps.first).readAsBytesSync());
      for (final s in ex.tables.values) {
        final rows = s.rows.map((r) => r.map((e) => e?.value?.toString() ?? '').toList()).toList();
        if (rows.isEmpty) continue;
        doc.addPage(pw.MultiPage(
            pageFormat: PdfPageFormat.a4.landscape,
            build: (_) => [pw.TableHelper.fromTextArray(data: rows, cellStyle: const pw.TextStyle(fontSize: 9))]));
      }
    }
    final out = await doc.save();
    if (c.mounted) await finish(c, out, 'converted');
  }),
  Tool(Icons.text_snippet_outlined, 'OCR', 'Image → text, offline', const Color(0xFF8B5CF6), (c) async {
    final r = await FilePicker.platform.pickFiles(type: FileType.image);
    final p = r?.paths.first;
    if (p == null) return;
    final t = await ocr(p);
    if (c.mounted) showText(c, t);
  }),
];

// ---------- shell ----------
class Shell extends StatefulWidget {
  const Shell({super.key});
  @override
  State<Shell> createState() => _ShellState();
}

class _ShellState extends State<Shell> {
  int i = 0;
  @override
  Widget build(BuildContext c) {
    final pages = [const Home(), const Files()];
    return Scaffold(
      body: AnimatedSwitcher(
          duration: 300.ms, child: KeyedSubtree(key: ValueKey(i), child: pages[i])),
      floatingActionButton: FloatingActionButton.extended(
          onPressed: () => Navigator.push(c, MaterialPageRoute(builder: (_) => const Scan())),
          icon: const Icon(Icons.document_scanner_outlined),
          label: const Text('Scan')).animate().scale(delay: 400.ms),
      bottomNavigationBar: NavigationBar(
          selectedIndex: i,
          onDestinationSelected: (v) => setState(() => i = v),
          destinations: const [
            NavigationDestination(icon: Icon(Icons.dashboard_outlined), label: 'Tools'),
            NavigationDestination(icon: Icon(Icons.folder_outlined), label: 'Files'),
          ]),
    );
  }
}

class Home extends StatelessWidget {
  const Home({super.key});
  @override
  Widget build(BuildContext c) {
    final cs = Theme.of(c).colorScheme;
    return CustomScrollView(slivers: [
      SliverToBoxAdapter(
          child: Container(
        padding: EdgeInsets.fromLTRB(24, MediaQuery.of(c).padding.top + 28, 24, 28),
        decoration: BoxDecoration(
            gradient: LinearGradient(colors: [cs.primary, cs.tertiary], begin: Alignment.topLeft, end: Alignment.bottomRight),
            borderRadius: const BorderRadius.vertical(bottom: Radius.circular(32))),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('PDF Forge', style: Theme.of(c).textTheme.headlineLarge?.copyWith(color: cs.onPrimary, fontWeight: FontWeight.w800)),
          const SizedBox(height: 6),
          Text('Scan, edit and convert — 100% offline', style: TextStyle(color: cs.onPrimary.withOpacity(.85))),
        ]),
      ).animate().fadeIn().slideY(begin: -.1)),
      SliverPadding(
          padding: const EdgeInsets.all(16),
          sliver: SliverGrid.builder(
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: 2, mainAxisSpacing: 14, crossAxisSpacing: 14, childAspectRatio: 1.15),
              itemCount: tools.length,
              itemBuilder: (_, k) {
                final t = tools[k];
                return Material(
                  color: t.color.withOpacity(.12),
                  borderRadius: BorderRadius.circular(22),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(22),
                    onTap: () async {
                      try { await t.run(c); } catch (e) { if (c.mounted) toast(c, 'Failed: $e'); }
                    },
                    child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          CircleAvatar(backgroundColor: t.color, child: Icon(t.icon, color: Colors.white)),
                          const Spacer(),
                          Text(t.title, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
                          Text(t.sub, style: const TextStyle(fontSize: 12)),
                        ])),
                  ),
                ).animate().fadeIn(delay: (80 * k).ms).slideY(begin: .2, curve: Curves.easeOutCubic);
              })),
      const SliverToBoxAdapter(child: SizedBox(height: 90)),
    ]);
  }
}

class Files extends StatefulWidget {
  const Files({super.key});
  @override
  State<Files> createState() => _FilesState();
}

class _FilesState extends State<Files> {
  List<File> files = [];
  @override
  void initState() { super.initState(); load(); }
  Future<void> load() async {
    final l = (await store()).listSync().whereType<File>().where((f) => f.path.endsWith('.pdf')).toList()
      ..sort((a, b) => b.lastModifiedSync().compareTo(a.lastModifiedSync()));
    if (mounted) setState(() => files = l);
  }
  @override
  Widget build(BuildContext c) => Scaffold(
      appBar: AppBar(title: const Text('My files'), actions: [
        IconButton(icon: const Icon(Icons.file_open_outlined), onPressed: () async {
          final p = await pick(['pdf']);
          if (p.isNotEmpty && c.mounted) openPdf(c, p.first);
        })
      ]),
      body: files.isEmpty
          ? const Center(child: Text('No documents yet — tap Scan'))
          : ListView.builder(
              itemCount: files.length,
              itemBuilder: (_, k) {
                final f = files[k];
                return Dismissible(
                  key: ValueKey(f.path),
                  background: Container(color: Colors.red, alignment: Alignment.centerRight, padding: const EdgeInsets.only(right: 20), child: const Icon(Icons.delete, color: Colors.white)),
                  direction: DismissDirection.endToStart,
                  onDismissed: (_) { f.deleteSync(); files.removeAt(k); },
                  child: ListTile(
                    leading: const Icon(Icons.picture_as_pdf, color: Colors.redAccent),
                    title: Text(f.path.split('/').last, maxLines: 1, overflow: TextOverflow.ellipsis),
                    subtitle: Text('${(f.lengthSync() / 1024).round()} KB'),
                    onTap: () => openPdf(c, f.path),
                  ).animate().fadeIn(delay: (40 * k).ms).slideX(begin: .1),
                );
              }));
}

// ---------- viewer ----------
class Viewer extends StatelessWidget {
  final String path;
  const Viewer(this.path, {super.key});
  @override
  Widget build(BuildContext c) => Scaffold(
      appBar: AppBar(title: Text(path.split('/').last, style: const TextStyle(fontSize: 15)), actions: [
        IconButton(icon: const Icon(Icons.view_agenda_outlined), tooltip: 'Organize',
            onPressed: () => Navigator.push(c, MaterialPageRoute(builder: (_) => Organize(path)))),
        IconButton(icon: const Icon(Icons.share), onPressed: () => Share.shareXFiles([XFile(path)])),
      ]),
      body: PdfViewer.file(path));
}

// ---------- scanner ----------
class Scan extends StatefulWidget {
  const Scan({super.key});
  @override
  State<Scan> createState() => _ScanState();
}

class _ScanState extends State<Scan> {
  List<String> pages = [];
  int filter = 0;
  bool busy = false;
  static const names = ['Original', 'Gray', 'B&W doc', 'Vivid'];

  @override
  void initState() { super.initState(); WidgetsBinding.instance.addPostFrameCallback((_) => add()); }

  Future<void> add() async {
    final r = await CunningDocumentScanner.getPictures(noOfPages: 30, isGalleryImportAllowed: true);
    if (r == null || r.isEmpty) { if (pages.isEmpty && mounted) Navigator.pop(context); return; }
    setState(() => pages.addAll(r));
  }

  Future<void> save() async {
    setState(() => busy = true);
    final out = await imagesToPdf(pages.map((p) => applyFilter(p, filter)).toList());
    final path = await savePdf(out, 'scan');
    if (!mounted) return;
    Navigator.pushReplacement(context, MaterialPageRoute(builder: (_) => Viewer(path)));
  }

  @override
  Widget build(BuildContext c) => Scaffold(
        appBar: AppBar(title: Text('${pages.length} page(s)'), actions: [
          IconButton(icon: const Icon(Icons.text_snippet_outlined), tooltip: 'OCR',
              onPressed: pages.isEmpty ? null : () async {
                final t = (await Future.wait(pages.map(ocr))).join('\n\n');
                if (c.mounted) showText(c, t);
              }),
        ]),
        body: busy
            ? const Center(child: CircularProgressIndicator())
            : Column(children: [
                Expanded(
                    child: ReorderableListView.builder(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.all(16),
                  itemCount: pages.length,
                  onReorder: (a, b) => setState(() { if (b > a) b--; pages.insert(b, pages.removeAt(a)); }),
                  itemBuilder: (_, k) => Padding(
                    key: ValueKey(pages[k]),
                    padding: const EdgeInsets.only(right: 12),
                    child: Stack(children: [
                      ClipRRect(borderRadius: BorderRadius.circular(16), child: AspectRatio(aspectRatio: .72, child: ColorFiltered(colorFilter: _cf(filter), child: Image.file(File(pages[k]), fit: BoxFit.cover)))),
                      Positioned(top: 4, right: 4, child: IconButton.filled(icon: const Icon(Icons.close, size: 18), onPressed: () => setState(() => pages.removeAt(k)))),
                    ]).animate().fadeIn().scale(begin: const Offset(.9, .9)),
                  ),
                )),
                SizedBox(height: 48, child: ListView(scrollDirection: Axis.horizontal, padding: const EdgeInsets.symmetric(horizontal: 12), children: [
                  for (var k = 0; k < names.length; k++)
                    Padding(padding: const EdgeInsets.only(right: 8), child: ChoiceChip(label: Text(names[k]), selected: filter == k, onSelected: (_) => setState(() => filter = k))),
                ])),
                Padding(padding: const EdgeInsets.all(16), child: Row(children: [
                  Expanded(child: OutlinedButton.icon(onPressed: add, icon: const Icon(Icons.add_a_photo_outlined), label: const Text('Add page'))),
                  const SizedBox(width: 12),
                  Expanded(child: FilledButton.icon(onPressed: pages.isEmpty ? null : save, icon: const Icon(Icons.picture_as_pdf), label: const Text('Save PDF'))),
                ])),
              ]),
      );

  ColorFilter _cf(int f) => switch (f) {
        1 => const ColorFilter.matrix([.33, .59, .11, 0, 0, .33, .59, .11, 0, 0, .33, .59, .11, 0, 0, 0, 0, 0, 1, 0]),
        2 => const ColorFilter.matrix([.7, 1.2, .2, 0, -40, .7, 1.2, .2, 0, -40, .7, 1.2, .2, 0, -40, 0, 0, 0, 1, 0]),
        3 => const ColorFilter.matrix([1.3, -.15, -.15, 0, 0, -.15, 1.3, -.15, 0, 0, -.15, -.15, 1.3, 0, 0, 0, 0, 0, 1, 0]),
        _ => const ColorFilter.mode(Colors.transparent, BlendMode.dst),
      };
}

// ---------- organize ----------
class Organize extends StatefulWidget {
  final String path;
  const Organize(this.path, {super.key});
  @override
  State<Organize> createState() => _OrgState();
}

class _OrgState extends State<Organize> {
  late List<int> order;
  final rot = <int, int>{};
  @override
  void initState() {
    super.initState();
    final d = sf.PdfDocument(inputBytes: File(widget.path).readAsBytesSync());
    order = List.generate(d.pages.count, (i) => i);
    d.dispose();
  }

  Future<void> export() async {
    final src = sf.PdfDocument(inputBytes: File(widget.path).readAsBytesSync());
    final dst = sf.PdfDocument();
    dst.pageSettings.margins.all = 0;
    for (final i in order) {
      final s = src.pages[i];
      dst.pageSettings.size = s.size;
      final p = dst.pages.add();
      p.graphics.drawPdfTemplate(s.createTemplate(), Offset.zero);
      p.rotation = sf.PdfPageRotateAngle.values[(rot[i] ?? 0) % 4];
    }
    final out = await dst.save();
    src.dispose();
    dst.dispose();
    if (mounted) {
      final p = await savePdf(out, 'edited');
      if (mounted) Navigator.pushReplacement(context, MaterialPageRoute(builder: (_) => Viewer(p)));
    }
  }

  @override
  Widget build(BuildContext c) => Scaffold(
        appBar: AppBar(title: const Text('Organize pages'), actions: [TextButton(onPressed: export, child: const Text('Save'))]),
        body: PdfDocumentViewBuilder.file(widget.path, builder: (ctx, doc) {
          if (doc == null) return const Center(child: CircularProgressIndicator());
          return ReorderableListView.builder(
            itemCount: order.length,
            onReorder: (a, b) => setState(() { if (b > a) b--; order.insert(b, order.removeAt(a)); }),
            itemBuilder: (_, k) {
              final pg = order[k];
              return ListTile(
                key: ValueKey(pg),
                leading: SizedBox(width: 56, height: 76, child: RotatedBox(quarterTurns: rot[pg] ?? 0, child: PdfPageView(document: doc, pageNumber: pg + 1))),
                title: Text('Page ${pg + 1}'),
                trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                  IconButton(icon: const Icon(Icons.rotate_right), onPressed: () => setState(() => rot[pg] = ((rot[pg] ?? 0) + 1) % 4)),
                  IconButton(icon: const Icon(Icons.delete_outline), onPressed: order.length > 1 ? () => setState(() => order.removeAt(k)) : null),
                ]),
              );
            },
          );
        }),
      );
}
