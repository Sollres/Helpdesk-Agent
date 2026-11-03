-- Historique de tickets fictifs, pour que findSimilarTickets ait des données dès le démarrage.
-- Leurs embeddings sont calculés au démarrage par TicketIndexer.
CREATE FUNCTION pg_temp.seed_ticket(p_title TEXT, p_description TEXT, p_category TEXT, p_priority TEXT, p_status TEXT,
                                    p_requester TEXT, p_resolution TEXT, p_age INTERVAL, p_comment TEXT)
    RETURNS VOID AS
$$
DECLARE
    v_id BIGINT;
BEGIN
    INSERT INTO tickets (reference, title, description, category, priority, status, requester, resolution, created_at, updated_at)
    VALUES ('HD-' || extract(YEAR FROM now() - p_age)::INT || '-' || lpad(nextval('ticket_reference_seq')::TEXT, 6, '0'),
            p_title, p_description, p_category, p_priority, p_status, p_requester, p_resolution,
            now() - p_age, now() - p_age / 2)
    RETURNING id INTO v_id;
    IF p_comment IS NOT NULL THEN
        INSERT INTO ticket_comments (ticket_id, author, content, created_at) VALUES (v_id, 'support', p_comment, now() - p_age / 2);
    END IF;
END;
$$ LANGUAGE plpgsql;

SELECT pg_temp.seed_ticket(
    'VPN : erreur « certificat non valide » après mise à jour',
    'Depuis la mise à jour automatique de GlobalProtect, la connexion échoue avec le message « Le certificat du serveur n''est pas valide ».',
    'NETWORK', 'HIGH', 'RESOLVED', 'lea.martin@entreprise.local',
    'La version 6.3 de GlobalProtect provoquait cette erreur. Solution : désinstaller GlobalProtect, redémarrer, puis le réinstaller depuis le Portail d''entreprise (version 6.3.1 corrigée).',
    INTERVAL '20 days', 'Correctif publié dans le Portail d''entreprise, ticket résolu.');

SELECT pg_temp.seed_ticket(
    'Copieur du 2e étage hors service',
    'Le copieur près de la salle de pause au 2e étage affiche « Erreur E-302 » et n''imprime plus rien.',
    'HARDWARE', 'MEDIUM', 'IN_PROGRESS', 'karim.benali@entreprise.local', NULL,
    INTERVAL '2 days', 'Intervention du technicien du prestataire prévue jeudi matin. En attendant, utilisez le copieur du 3e étage.');

SELECT pg_temp.seed_ticket(
    'Wi-Fi Corp-WiFi instable en salle Everest',
    'Dans la salle de réunion Everest (4e étage), le Wi-Fi Corp-WiFi se coupe toutes les cinq minutes pendant les visioconférences.',
    'NETWORK', 'MEDIUM', 'RESOLVED', 'sophie.durand@entreprise.local',
    'La borne Wi-Fi de la salle était défectueuse et a été remplacée.',
    INTERVAL '35 days', NULL);

SELECT pg_temp.seed_ticket(
    'Outlook : messages bloqués dans la boîte d''envoi',
    'Plusieurs collaborateurs signalent que leurs e-mails restent dans la boîte d''envoi d''Outlook et ne partent pas.',
    'SOFTWARE', 'CRITICAL', 'IN_PROGRESS', 'support@entreprise.local', NULL,
    INTERVAL '3 hours', 'Incident Microsoft 365 en cours chez l''éditeur. Les messages partiront automatiquement une fois le service rétabli, inutile de les renvoyer.');

SELECT pg_temp.seed_ticket(
    'Accès refusé au lecteur S:\Finance',
    'Message « Accès refusé » en ouvrant S:\Finance\Budget depuis mon arrivée dans l''équipe contrôle de gestion.',
    'ACCESS', 'MEDIUM', 'RESOLVED', 'thomas.petit@entreprise.local',
    'Droits en modification ajoutés après validation de la responsable du dossier. Verrouiller puis déverrouiller la session pour les appliquer.',
    INTERVAL '12 days', NULL);

SELECT pg_temp.seed_ticket(
    'Teams : écran noir lors du partage d''écran',
    'Quand je partage mon écran dans Teams, les participants voient un écran noir.',
    'SOFTWARE', 'LOW', 'RESOLVED', 'julie.moreau@entreprise.local',
    'Désactiver l''accélération matérielle du processeur graphique dans Teams (Paramètres > Général), puis redémarrer Teams.',
    INTERVAL '45 days', NULL);

SELECT pg_temp.seed_ticket(
    'Demande d''installation de Python 3.12',
    'Besoin de Python 3.12 pour des scripts d''analyse de données, absent du Portail d''entreprise.',
    'SOFTWARE', 'LOW', 'OPEN', 'antoine.leroy@entreprise.local', NULL,
    INTERVAL '1 day', 'En attente de la validation du manager.');

SELECT pg_temp.seed_ticket(
    'Station d''accueil : second écran non détecté',
    'Le second écran branché sur la station d''accueil n''affiche rien, alors que le premier fonctionne.',
    'HARDWARE', 'MEDIUM', 'RESOLVED', 'camille.roux@entreprise.local',
    'Mettre à jour le firmware de la station d''accueil avec l''application « Dock Firmware Updater » du Portail d''entreprise, puis débrancher et rebrancher la station.',
    INTERVAL '25 days', NULL);

SELECT pg_temp.seed_ticket(
    'Compte verrouillé en boucle',
    'Mon compte se verrouille plusieurs fois par jour depuis que j''ai changé mon mot de passe.',
    'ACCESS', 'HIGH', 'CLOSED', 'nicolas.faure@entreprise.local',
    'L''ancien mot de passe était encore enregistré sur la tablette personnelle (application Outlook). Mot de passe mis à jour sur la tablette.',
    INTERVAL '60 days', NULL);

SELECT pg_temp.seed_ticket(
    'Excel se ferme à l''ouverture des fichiers partagés',
    'Excel se ferme brutalement quand j''ouvre un fichier depuis un site SharePoint.',
    'SOFTWARE', 'MEDIUM', 'RESOLVED', 'emma.girard@entreprise.local',
    'Réparation en ligne d''Office : Paramètres Windows > Applications > Microsoft 365 > Modifier > Réparation en ligne.',
    INTERVAL '15 days', NULL);
