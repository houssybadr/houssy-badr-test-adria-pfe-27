import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import 'api.dart';
import 'operation_dialog.dart';

void main() => runApp(const AdriaApp());

class AdriaApp extends StatelessWidget {
  const AdriaApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'ADRIA Bank',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff155e75)),
      scaffoldBackgroundColor: const Color(0xfff6f8fa),
    ),
    home: const BankHome(),
  );
}

class BankHome extends StatefulWidget {
  const BankHome({super.key});
  @override
  State<BankHome> createState() => _BankHomeState();
}

class _BankHomeState extends State<BankHome> {
  late final BankApi api;
  List<Map<String, dynamic>> accounts = [];
  List<Map<String, dynamic>> transactions = [];
  final messages = <Map<String, String>>[];
  final chatInput = TextEditingController();
  int tab = 0;
  bool loading = true, sending = false;
  String? error, filterId;

  @override
  void initState() {
    super.initState();
    const configured = String.fromEnvironment('API_URL');
    final host = !kIsWeb && defaultTargetPlatform == TargetPlatform.android
        ? '10.0.2.2'
        : 'localhost';
    api = BankApi(configured.isEmpty ? 'http://$host:8080' : configured);
    reload();
  }

  @override
  void dispose() {
    chatInput.dispose();
    super.dispose();
  }

  Future<void> reload() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final results = await Future.wait([
        api.call('GET', '/api/accounts'),
        api.call('GET', '/api/transactions'),
      ]);
      if (!mounted) return;
      setState(() {
        accounts = (results[0] as List)
            .map((a) => Map<String, dynamic>.from(a as Map))
            .toList();
        transactions = (results[1] as List)
            .map((t) => Map<String, dynamic>.from(t as Map))
            .toList();
        loading = false;
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          loading = false;
          error =
              'Connexion aux services impossible. Vérifie l’adresse de la Gateway. $e';
        });
      }
    }
  }

  Future<void> result(String title, dynamic data) async {
    if (!mounted) return;
    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: SizedBox(
          width: 560,
          child: SingleChildScrollView(
            child: SelectableText(
              pretty(data),
              style: const TextStyle(fontFamily: 'monospace', fontSize: 12),
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Fermer'),
          ),
        ],
      ),
    );
  }

  Future<void> operation(String kind, [String? id]) async {
    if (kind != 'account' && accounts.isEmpty) {
      await result('Crée un compte pour commencer', {});
      return;
    }
    final receipt = await showDialog<Map<String, dynamic>>(
      context: context,
      barrierDismissible: false,
      builder: (_) => OperationDialog(
        api: api,
        accounts: accounts,
        kind: kind,
        accountId: id,
      ),
    );
    if (receipt != null) {
      await reload();
      await result('Réponse · ${receipt['path']}', receipt['data']);
    }
  }

  Future<void> inspect(String path, String title) async {
    try {
      await result(title, await api.call('GET', path));
    } catch (e) {
      await result('Erreur', e.toString());
    }
  }

  Future<void> status(Map<String, dynamic> account) async {
    final next = account['status'] == 'ACTIVE' ? 'BLOCKED' : 'ACTIVE';
    try {
      final data = await api.call(
        'PATCH',
        '/api/accounts/${account['id']}/status',
        {'status': next},
      );
      await reload();
      await result('Statut mis à jour', data);
    } catch (e) {
      await result('Erreur', e.toString());
    }
  }

  Future<void> settings() async {
    final controller = TextEditingController(text: api.baseUrl);
    final value = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Adresse des services'),
        content: TextField(
          controller: controller,
          decoration: const InputDecoration(
            labelText: 'Gateway',
            helperText: 'Android émulateur : http://10.0.2.2:8080\nTéléphone : http://IP-DU-PC:8080',
            helperMaxLines: 3,
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Annuler'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text.trim()),
            child: const Text('Connecter'),
          ),
        ],
      ),
    );
    // The dialog's closing animation may still reference its controller.
    await Future<void>.delayed(const Duration(milliseconds: 250));
    controller.dispose();
    if (value != null && value.isNotEmpty) {
      final uri = Uri.tryParse(value);
      if (uri == null ||
          !['http', 'https'].contains(uri.scheme) ||
          uri.host.isEmpty) {
        await result(
          'Adresse invalide',
          'Utilise une adresse http:// ou https://',
        );
        return;
      }
      api.baseUrl = value.replaceFirst(RegExp(r'/+$'), '');
      await reload();
    }
  }

  Widget heading(String title, String subtitle, [Widget? action]) => Padding(
    padding: const EdgeInsets.only(bottom: 16),
    child: Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: Theme.of(context).textTheme.headlineSmall),
              Text(subtitle, style: const TextStyle(color: Colors.black54)),
            ],
          ),
        ),
        ?action,
      ],
    ),
  );

  Widget accountPage() => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      heading(
        'Comptes',
        '${accounts.length} comptes disponibles',
        FilledButton.icon(
          onPressed: () => operation('account'),
          icon: const Icon(Icons.add),
          label: const Text('Créer'),
        ),
      ),
      if (accounts.isEmpty && !loading)
        const Card(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: Text('Aucun compte. Crée ton premier compte.'),
          ),
        ),
      for (final a in accounts)
        Card(
          margin: const EdgeInsets.only(bottom: 12),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        '${a['customerReference']}',
                        style: const TextStyle(
                          fontWeight: FontWeight.w600,
                          fontSize: 16,
                        ),
                      ),
                    ),
                    Chip(
                      label: Text(a['status'] == 'ACTIVE' ? 'Actif' : 'Bloqué'),
                      backgroundColor: a['status'] == 'ACTIVE'
                          ? const Color(0xffdcfce7)
                          : const Color(0xfffee2e2),
                    ),
                  ],
                ),
                Text(
                  '${money(a['balance'])} ${a['currency']}',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 6),
                SelectableText(
                  '${a['id']}',
                  style: const TextStyle(fontSize: 11, color: Colors.black54),
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 4,
                  children: [
                    TextButton(
                      onPressed: () => inspect(
                        '/api/accounts/${a['id']}',
                        'Détails du compte',
                      ),
                      child: const Text('Détails'),
                    ),
                    TextButton(
                      onPressed: () => inspect(
                        '/api/accounts/${a['id']}/balance',
                        'Solde du compte',
                      ),
                      child: const Text('Solde'),
                    ),
                    TextButton(
                      onPressed: () => operation('credit', '${a['id']}'),
                      child: const Text('Créditer'),
                    ),
                    TextButton(
                      onPressed: () => operation('debit', '${a['id']}'),
                      child: const Text('Débiter'),
                    ),
                    TextButton(
                      onPressed: () => status(a),
                      child: Text(
                        a['status'] == 'ACTIVE' ? 'Bloquer' : 'Activer',
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
    ],
  );

  String name(dynamic id) {
    final found = accounts.where((a) => a['id'] == id);
    return found.isEmpty ? '$id' : '${found.first['customerReference']}';
  }

  Widget transactionPage() {
    final visible = transactions
        .where(
          (t) =>
              filterId == null ||
              t['accountId'] == filterId ||
              t['recipientAccountId'] == filterId,
        )
        .toList();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        heading('Opérations', '${visible.length} paiements et virements'),
        Wrap(
          spacing: 10,
          runSpacing: 8,
          children: [
            FilledButton.icon(
              onPressed: () => operation('payment'),
              icon: const Icon(Icons.add_card),
              label: const Text('Paiement'),
            ),
            OutlinedButton.icon(
              onPressed: () => operation('transfer'),
              icon: const Icon(Icons.swap_horiz),
              label: const Text('Virement'),
            ),
          ],
        ),
        const SizedBox(height: 16),
        DropdownButtonFormField<String>(
          key: ValueKey(filterId),
          initialValue: filterId ?? '',
          isExpanded: true,
          decoration: const InputDecoration(
            labelText: 'Filtrer par compte',
            border: OutlineInputBorder(),
          ),
          items: [
            const DropdownMenuItem(value: '', child: Text('Tous les comptes')),
            ...accounts.map(
              (a) => DropdownMenuItem(
                value: '${a['id']}',
                child: Text(
                  '${a['customerReference']}',
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ),
          ],
          onChanged: (id) => setState(() => filterId = id == '' ? null : id),
        ),
        const SizedBox(height: 16),
        if (visible.isEmpty)
          const Card(
            child: Padding(
              padding: EdgeInsets.all(24),
              child: Text('Aucune opération pour ce compte.'),
            ),
          ),
        for (final t in visible)
          Card(
            margin: const EdgeInsets.only(bottom: 10),
            child: ListTile(
              contentPadding: const EdgeInsets.all(14),
              leading: CircleAvatar(
                child: Icon(
                  t['recipientAccountId'] != null
                      ? Icons.swap_horiz
                      : Icons.shopping_bag_outlined,
                ),
              ),
              title: Text(
                '${money(t['amount'])} ${t['currency']}',
                style: const TextStyle(fontWeight: FontWeight.w600),
              ),
              subtitle: Text(
                '${name(t['accountId'])}${t['recipientAccountId'] != null ? '\n→ ${name(t['recipientAccountId'])}' : '\n${t['merchantCategory']} · ${t['country']}'}\n${t['status']} · ${DateTime.tryParse('${t['createdAt']}')?.toLocal().toString().substring(0, 16) ?? ''}',
              ),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => inspect(
                '/api/transactions/${t['id']}',
                'Détails de l’opération',
              ),
            ),
          ),
      ],
    );
  }

  Future<void> sendChat() async {
    final text = chatInput.text.trim();
    if (text.isEmpty || sending) return;
    setState(() {
      sending = true;
      messages.add({'role': 'user', 'text': text});
      chatInput.clear();
    });
    try {
      final response = await api.call('POST', '/api/chat', {'message': text});
      if (mounted) {
        setState(
          () => messages.add({
            'role': 'assistant',
            'text': '${response['answer']}',
          }),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => messages.add({'role': 'error', 'text': e.toString()}));
      }
    } finally {
      if (mounted) setState(() => sending = false);
    }
  }

  Widget chatPage() => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      heading('Assistant bancaire', 'Gemini · outils MCP'),
      const Card(
        child: Padding(
          padding: EdgeInsets.all(16),
          child: Text(
            'Pose une question complète avec le compte concerné. Pour un paiement ou un virement, précise le montant et la devise.',
          ),
        ),
      ),
      const SizedBox(height: 8),
      OutlinedButton.icon(
        onPressed: sending
            ? null
            : () {
                final demos = accounts.where(
                  (a) => a['customerReference'] == 'ADRIA-DEMO-A',
                );
                if (demos.isNotEmpty) {
                  chatInput.text =
                      'Consulte via MCP le solde du compte de démonstration ${demos.first['id']}.';
                }
              },
        icon: const Icon(Icons.edit_note),
        label: const Text('Préparer une question de démonstration'),
      ),
      for (final message in messages)
        Align(
          alignment: message['role'] == 'user'
              ? Alignment.centerRight
              : Alignment.centerLeft,
          child: Container(
            margin: const EdgeInsets.symmetric(vertical: 6),
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: message['role'] == 'user'
                  ? const Color(0xffdceef3)
                  : message['role'] == 'error'
                  ? const Color(0xfffee2e2)
                  : Colors.white,
              borderRadius: BorderRadius.circular(12),
            ),
            child: SelectableText(message['text']!),
          ),
        ),
      if (sending)
        const Padding(
          padding: EdgeInsets.all(16),
          child: LinearProgressIndicator(),
        ),
      const SizedBox(height: 12),
      TextField(
        controller: chatInput,
        minLines: 2,
        maxLines: 5,
        maxLength: 4000,
        decoration: const InputDecoration(
          labelText: 'Ton message',
          border: OutlineInputBorder(),
        ),
      ),
      FilledButton.icon(
        onPressed: sending ? null : sendChat,
        icon: const Icon(Icons.send_outlined),
        label: Text(sending ? 'Réponse en cours…' : 'Envoyer'),
      ),
    ],
  );

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('ADRIA Bank'),
      actions: [
        IconButton(
          tooltip: 'Actualiser',
          onPressed: loading ? null : reload,
          icon: const Icon(Icons.refresh),
        ),
        IconButton(
          tooltip: 'Connexion',
          onPressed: settings,
          icon: const Icon(Icons.settings_outlined),
        ),
      ],
    ),
    body: SafeArea(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 900),
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              if (loading) const LinearProgressIndicator(),
              if (error != null)
                Card(
                  color: const Color(0xfffee2e2),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      children: [
                        Text(error!),
                        TextButton(
                          onPressed: reload,
                          child: const Text('Réessayer'),
                        ),
                      ],
                    ),
                  ),
                ),
              switch (tab) {
                0 => accountPage(),
                1 => transactionPage(),
                _ => chatPage(),
              },
              const SizedBox(height: 24),
            ],
          ),
        ),
      ),
    ),
    bottomNavigationBar: NavigationBar(
      selectedIndex: tab,
      onDestinationSelected: (value) => setState(() => tab = value),
      destinations: const [
        NavigationDestination(
          icon: Icon(Icons.account_balance_wallet_outlined),
          label: 'Comptes',
        ),
        NavigationDestination(
          icon: Icon(Icons.swap_horiz),
          label: 'Opérations',
        ),
        NavigationDestination(
          icon: Icon(Icons.chat_bubble_outline),
          label: 'Assistant',
        ),
      ],
    ),
  );
}
