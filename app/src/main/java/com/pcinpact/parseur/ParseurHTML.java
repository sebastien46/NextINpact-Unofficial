/*
 * Copyright 2013 - 2019 Anael Mobilia and contributors
 * 
 * This file is part of NextINpact-Unofficial.
 * 
 * NextINpact-Unofficial is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * NextINpact-Unofficial is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with NextINpact-Unofficial. If not, see <http://www.gnu.org/licenses/>
 */
package com.pcinpact.parseur;

import android.util.Log;

import com.pcinpact.R;
import com.pcinpact.items.ArticleItem;
import com.pcinpact.items.CommentaireItem;
import com.pcinpact.utils.Constantes;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parseur du code HTML.
 *
 * @author Anael
 */
public class ParseurHTML {
    /**
     * Parse la liste des articles.
     *
     * @param unContenu contenu HTML brut
     * @param urlPage   URL de la page
     * @return liste d'articleItem
     */
    public static ArrayList<ArticleItem> getListeArticles(final String unContenu, final String urlPage) {
        ArrayList<ArticleItem> mesArticlesItem = new ArrayList<>();

        // Lancement du parseur sur la page
        Document pageNXI = Jsoup.parse(unContenu, urlPage);

        // Les articles
        Elements lesArticles = pageNXI.select("article[data-acturowid][data-datepubli]");

        ArticleItem monArticleItem;
        // Pour chaque article
        for (Element unArticle : lesArticles) {
            monArticleItem = new ArticleItem();

            // ID de l'article
            monArticleItem.setId(Integer.valueOf(unArticle.attr("data-acturowid")));

            // Date de publication de l'article
            String laDate = unArticle.attr("data-datepubli");
            monArticleItem.setTimeStampPublication(convertToTimeStamp(laDate, Constantes.FORMAT_DATE_ARTICLE));

            // URL de l'illustration
            String urlIllustration = "";
            Elements lesImages = unArticle.select("img[class=ded-image]");
            if (!lesImages.isEmpty()) {
                Element image = lesImages.get(0);
                urlIllustration = image.absUrl("data-frz-src");
            } else {
                if (Constantes.DEBUG) {
                    Log.e("ParseurHTML", "getListeArticles - Pb illustration");
                }
            }
            monArticleItem.setUrlIllustration(urlIllustration);

            // URL de l'article
            String urlArticle = "";
            String titreArticle = "";
            Elements lesUrl = unArticle.select("h1 > a[href]");
            if (!lesUrl.isEmpty()) {
                Element url = lesUrl.get(0);
                urlArticle = url.absUrl("href");

                // Titre de l'article (liée à l'URL)
                titreArticle = url.text();
            } else {
                if (Constantes.DEBUG) {
                    Log.e("ParseurHTML", "getListeArticles - Pb URL article");
                }
            }
            monArticleItem.setUrl(urlArticle);
            monArticleItem.setTitre(titreArticle);

            // Sous titre
            String monSousTitre = "";
            Elements lesSousTitres = unArticle.select("span[class=soustitre]");
            if (!lesSousTitres.isEmpty()) {
                Element unSousTitre = lesSousTitres.get(0);
                // Je supprime le "- " en début du sous titre
                monSousTitre = unSousTitre.text().substring(2);
            } else {
                if (Constantes.DEBUG) {
                    Log.e("ParseurHTML", "getListeArticles - Pb sous titre");
                }
            }
            monArticleItem.setSousTitre(monSousTitre);

            // Nombre de commentaires
            int nbCommentaires = 0;
            Elements lesCommentaires = unArticle.select("span[class=nb_comments]");
            if (!lesCommentaires.isEmpty()) {
                Element commentaires = lesCommentaires.get(0);
                try {
                    nbCommentaires = Integer.valueOf(commentaires.text());
                } catch (NumberFormatException e) {
                    // Nouveaux commentaires : "172 + 5"
                    String valeur = commentaires.text();

                    // Récupération des éléments
                    int positionOperateur = valeur.indexOf("+");
                    String membreGauche = valeur.substring(0, positionOperateur).trim();
                    String membreDroit = valeur.substring(positionOperateur + 1).trim();

                    // On additionne
                    nbCommentaires = Integer.valueOf(membreGauche) + Integer.valueOf(membreDroit);
                }
            } else {
                if (Constantes.DEBUG) {
                    Log.e("ParseurHTML", "getListeArticles - Pb nb Commentaires");
                }
            }
            monArticleItem.setNbCommentaires(nbCommentaires);

            // Statut abonné
            Elements badgeAbonne = unArticle.select("img[alt=badge_abonne]");
            // Ai-je trouvé des éléments ?
            if (badgeAbonne.size() > 0) {
                monArticleItem.setAbonne(true);
                // DEBUG
                if (Constantes.DEBUG) {
                    Log.w("ParseurHTML", "getListeArticles() - [abonné] => " + monArticleItem.getTitre());
                }
            } else {
                monArticleItem.setAbonne(false);
            }

            // Et je le stocke
            mesArticlesItem.add(monArticleItem);
        }

        return mesArticlesItem;
    }

    /**
     * Parse le contenu d'un article.
     *
     * @param unContenu contenu HTML brut
     * @param urlPage   URL de la page
     * @return ArticleItem
     */
    public static ArticleItem getArticle(final String unContenu, final String urlPage) {
        ArticleItem monArticleItem = new ArticleItem();

        // Lancement du parseur sur la page
        Document pageNXI = Jsoup.parse(unContenu, urlPage);
        // L'article
        Elements lArticle = pageNXI.select("article");

        // L'ID de l'article
        int unID = 0;
        // Regexp pour récupérer l'ID numérique dans l'URL
        Pattern p = Pattern.compile("[^\\d]*([\\d]+)[^\\d]");
        Matcher m = p.matcher(urlPage);
        if (m.find()) {
            unID = Integer.valueOf(m.group(1));
        } else {
            if (Constantes.DEBUG) {
                Log.e("ParseurHTML", "getArticle - Pb ID");
            }
        }
        monArticleItem.setId(unID);

        // NETTOYAGE DU CONTENU
        // Image article
        lArticle.select("section[id=article-dedicated]").remove();
        // Légende image article
        lArticle.select("div[class=thumb-cat-container]").remove();
        // Liens réseaux sociaux + temps de lecture
        lArticle.select("div[class=rs-content]").remove();

        // Affiliation
        Elements lesAffiliations = lArticle.select("span[data-affiliable][data-affkey]");
        for (Element uneAffiliation : lesAffiliations) {
            // Injection du contenu avant la balise
            uneAffiliation.before(uneAffiliation.text());
            // Suppression de la balise <span>
            uneAffiliation.remove();
        }

        // #Brief - suppression des liens sur les titres d'article
        Elements leBriefLiens = lArticle.select("h2 > a[href*=/brief/]");
        // Récupération de toutes les balises <a...> autour du titre
        for (Element unLienTitreArticleBrief : leBriefLiens) {
            // Insertion du titre en h2
            unLienTitreArticleBrief.before(unLienTitreArticleBrief.html());
            // Suppression du lien originel
            unLienTitreArticleBrief.remove();
        }

        // # Brief - suppression des nombres de commentaires & réseaux sociaux & couleurs
        lArticle.select("div[class=brief-foot], div[class=brief-circle-container]").remove();

        // Gestion des iframe
        Elements lesIframes = lArticle.select("iframe");
        // généralisation de l'URL en dehors du scheme
        String[] schemes = { "https://", "http://", "//" };
        // Pour chaque iframe
        for (Element uneIframe : lesIframes) {
            // URL du lecteur
            String urlLecteur = uneIframe.attr("src").toLowerCase(Constantes.LOCALE);

            for (String unScheme : schemes) {
                if (urlLecteur.startsWith(unScheme)) {
                    // Suppression du scheme
                    urlLecteur = urlLecteur.substring(unScheme.length());
                    // DEBUG
                    if (Constantes.DEBUG) {
                        Log.w("ParseurHTML", "getArticle() - Iframe : utilisation du scheme " + unScheme + " => " + urlLecteur);
                    }
                }
            }

            // ID de la vidéo
            String idVideo = urlLecteur.substring(urlLecteur.lastIndexOf("/") + 1).split("\\?")[0].split("#")[0];

            // Ma substitution
            String monRemplacement = "";

            // Gestion des lecteurs vidéos
            if (urlLecteur.startsWith("www.youtube.com/embed/videoseries")) {
                /**
                 * Liste de lecture Youtube
                 */
                // Recalcul de l'ID de la vidéo (cas particulier)
                idVideo = urlLecteur.substring(urlLecteur.lastIndexOf("list=") + "list=".length()).split("\\?")[0].split("#")[0];
                monRemplacement = "<a href=\"http://www.youtube.com/playlist?list=" + idVideo + "\"><img src=\""
                                  + Constantes.SCHEME_IFRAME_DRAWABLE + R.drawable.iframe_liste_youtube + "\" /></a>";
            } else if (urlLecteur.startsWith("www.youtube.com/embed/") || urlLecteur.startsWith(
                    "www.youtube-nocookie.com/embed/")) {
                /**
                 * Youtube
                 */
                monRemplacement = "<a href=\"http://www.youtube.com/watch?v=" + idVideo + "\"><img src=\""
                                  + Constantes.SCHEME_IFRAME_DRAWABLE + R.drawable.iframe_youtube + "\" /></a>";
            } else if (urlLecteur.startsWith("www.dailymotion.com/embed/video/")) {
                /**
                 * Dailymotion
                 */
                monRemplacement = "<a href=\"http://www.dailymotion.com/video/" + idVideo + "\"><img src=\""
                                  + Constantes.SCHEME_IFRAME_DRAWABLE + R.drawable.iframe_dailymotion + "\" /></a>";
            } else if (urlLecteur.startsWith("player.vimeo.com/video/")) {
                /**
                 * VIMEO
                 */
                monRemplacement =
                        "<a href=\"http://www.vimeo.com/" + idVideo + "\"><img src=\"" + Constantes.SCHEME_IFRAME_DRAWABLE
                        + R.drawable.iframe_vimeo + "\" /></a>";
            } else if (urlLecteur.startsWith("static.videos.gouv.fr/player/video/")) {
                /**
                 * Videos.gouv.fr
                 */
                monRemplacement = "<a href=\"http://static.videos.gouv.fr/player/video/" + idVideo + "\"><img src=\""
                                  + Constantes.SCHEME_IFRAME_DRAWABLE + R.drawable.iframe_videos_gouv_fr + "\" /></a>";
            } else if (urlLecteur.startsWith("vid.me")) {
                /**
                 * Vidme
                 */
                monRemplacement = "<a href=\"https://vid.me/" + idVideo + "\"><img src=\"" + Constantes.SCHEME_IFRAME_DRAWABLE
                                  + R.drawable.iframe_vidme + "\" /></a>";
            } else if (urlLecteur.startsWith("w.soundcloud.com/player/")) {
                /**
                 * Soundcloud (l'URL commence bien par w.soundcloud !)
                 */
                monRemplacement = "<a href=\"" + urlLecteur + "\"><img src=\"" + Constantes.SCHEME_IFRAME_DRAWABLE
                                  + R.drawable.iframe_soundcloud + "\" /></a>";
            } else if (urlLecteur.startsWith("www.scribd.com/embeds/")) {
                /**
                 * Scribd
                 */
                monRemplacement = "<a href=\"" + urlLecteur + "\"><img src=\"" + Constantes.SCHEME_IFRAME_DRAWABLE
                                  + R.drawable.iframe_scribd + "\" /></a>";
            } else if (urlLecteur.startsWith("player.canalplus.fr/embed/")) {
                /**
                 * Canal+
                 */
                monRemplacement = "<a href=\"" + urlLecteur + "\"><img " + "src=\"" + Constantes.SCHEME_IFRAME_DRAWABLE
                                  + R.drawable.iframe_canalplus + "\" /></a>";
            } else if (urlLecteur.startsWith("www.arte.tv/")) {
                /**
                 * Arte
                 */
                monRemplacement = "<a href=\"" + urlLecteur + "\"><img " + "src=\"" + Constantes.SCHEME_IFRAME_DRAWABLE
                                  + R.drawable.iframe_arte + "\" /></a>";
            } else {
                /**
                 * Déchet (catch all)
                 */
                monRemplacement =
                        "<a href=\"" + uneIframe.absUrl("src") + "\"><img " + "src=\"" + Constantes.SCHEME_IFRAME_DRAWABLE
                        + R.drawable.iframe_non_supportee + "\" /></a>";

                // DEBUG
                if (Constantes.DEBUG) {
                    Log.e("ParseurHTML",
                          "getArticle() - Iframe non gérée dans " + monArticleItem.getId() + " : " + uneIframe.absUrl("src"));
                }
            }


            // Je remplace l'iframe par mon contenu
            uneIframe.before(monRemplacement);
            uneIframe.remove();

            // DEBUG
            if (Constantes.DEBUG) {
                Log.i("ParseurHTML", "Remplacement par une iframe : " + monRemplacement);
            }
        }

        // Gestion des URL relatives des liens
        Elements lesLiens = lArticle.select("a[href]");
        // Pour chaque lien
        for (Element unLien : lesLiens) {
            // Assignation de son URL absolue
            unLien.attr("href", unLien.absUrl("href"));
        }
        // Gestion des URL relatives des images
        Elements lesImages = lArticle.select("img[src]");
        // Pour chaque image
        for (Element uneImage : lesImages) {
            // Assignation de son URL absolue
            uneImage.attr("src", uneImage.absUrl("src"));
        }


        /**
         * Gestion des images #232
         */
        // Standard
        // https://m.nextinpact.com/news/105343-orange-remise-coupleede-1998-avec-epresse-et-izneo-famille-by-canal-passe-a-12.htm
        // <p style="text-align: center;"><img src="https://cdn2.nextinpact.com/images/bd/news/168187.png" alt="Orange Bouquet
        // => Rien à faire !

        // fancyimg
        // https://m.nextinpact.com/news/105343-orange-remise-coupleede-1998-avec-epresse-et-izneo-famille-by-canal-passe-a-12.htm
        // <p style="text-align: center;
        // "><a class="fancyimg" href="https://cdn2.nextinpact.com/images/bd/news/168188.png" rel="group_fancy"><img
        // src="https://cdn2.nextinpact.com/images/bd/news/mini-168188.png" alt="Orange ePresse izneo" height="216"
        // /></a><a class="fancyimg" href="https://cdn2.nextinpact.com/images/bd/news/168189.png" rel="group_fancy"><img
        // src="https://cdn2.nextinpact.com/images/bd/news/mini-168189.png" alt="Orange ePresse izneo" /><br /></a><span
        // style="font-size: smaller; font-weight: bold;">ePresse avec Orange et izneo, également avec Orange</span></p>
        Elements liensImagesFancy = lArticle.select("a[class=fancyimg]:has(img)");
        // Pour chaque <a>
        for (Element lienImageFancy : liensImagesFancy) {
            // Pour chaque image...
            for (Element lImage : lienImageFancy.select("img")) {
                // Passage à l'image pleine taille
                lImage.attr("src", lienImageFancy.absUrl("href"));
                // Injection de l'image pleine taille...
                lienImageFancy.before("<p>" + lImage.outerHtml() + "</p>");
            }
            // Suppression du lien (et ses enfants)
            lienImageFancy.remove();
        }

        // slideshow-container
        // https://m.nextinpact.com/news/105361-pcspecialist-arrive-en-france-avec-ses-pc-fixes-et-portables-personnalisables.htm
        // <ul class="slideshow-container"><li><a href="https://cdn2.nextinpact.com/images/bd/news/168239.png"><img
        // src="https://cdn2.nextinpact.com/images/bd/news/mini-168239.png" alt="PCSpecialist"
        // data-large-src="https://cdn2.nextinpact.com/images/bd/news/168239.png"
        // /></a></li><li><a href="https://cdn2.nextinpact.com/images/bd/news/168240.png"><img src="https://cdn2.nextinpact
        // .com/images/bd/news/mini-168240.png" alt="PCSpecialist" data-large-src="https://cdn2.nextinpact
        // .com/images/bd/news/168240.png" /></a></li><li><a href="https://cdn2.nextinpact.com/images/bd/news/168241.png"><img
        // src="https://cdn2.nextinpact.com/images/bd/news/mini-168241.png" alt="PCSpecialist"
        // data-large-src="https://cdn2.nextinpact.com/images/bd/news/168241.png"
        // /></a></li><li><a href="https://cdn2.nextinpact.com/images/bd/news/168242.png"><img src="https://cdn2.nextinpact
        // .com/images/bd/news/mini-168242.png" alt="PCSpecialist" data-large-src="https://cdn2.nextinpact
        // .com/images/bd/news/168242.png" /></a></li></ul>
        Elements lesSlideShow = lArticle.select("ul[class=slideshow-container]:has(li > a > img)");
        // Pour chaque slideshow
        for (Element unSlideShow : lesSlideShow) {
            // Pour chaque <img> du slideshow !
            for (Element imageSlideShow : unSlideShow.select("img")) {
                // Prise de l'image en pleine taille
                imageSlideShow.attr("src", imageSlideShow.absUrl("data-large-src"));
                // Injection de l'image pleine taille...
                unSlideShow.before("<p>" + imageSlideShow.outerHtml() + "</p>");
            }
            // Suppression du slideshow
            unSlideShow.remove();
        }


        // Elimination des htmlentities (beacoup de &nbsp;)
        String monArticle = Parser.unescapeEntities(lArticle.toString(), true);

        // J'enregistre le contenu
        monArticleItem.setContenu(monArticle);

        return monArticleItem;
    }

    /**
     * Nombre de commentaires d'un article à partir d'une page de commentaires.
     *
     * @param unContenu contenu HTML brut
     * @param urlPage   URL de la page
     * @return nb de commentaires de l'article
     */
    public static int getNbCommentaires(final String unContenu, final String urlPage) {
        // Lancement du parseur sur la page
        Document pageNXI = Jsoup.parse(unContenu, urlPage);
        // Nombre de commentaires
        Element elementNbComms = pageNXI.select("span[class=actu_separator_comms]").get(0);

        // Représentation textuelle "nn commentaires"
        String stringNbComms = elementNbComms.text();

        // Isolation du chiffre uniquement (avant l'espace)
        int positionEspace = stringNbComms.indexOf(" ");
        String valeur = stringNbComms.substring(0, positionEspace).trim();

        // Parsage de la valeur
        int nbComms = 0;
        try {
            // Si aucun, retour textuel...
            nbComms = Integer.valueOf(valeur);
        } catch (NumberFormatException e) {
            if (Constantes.DEBUG) {
                Log.e("ParseurHTML", "getNbCommentaires() - Erreur nb comms", e);
            }
        }

        // DEBUG
        if (Constantes.DEBUG) {
            Log.i("ParseurHTML", "getNbCommentaires() - " + nbComms);
        }

        return nbComms;
    }

    /**
     * Parse les commentaires.
     *
     * @param unContenu contenu HTML brut
     * @param urlPage   URL de la page
     * @return liste de CommentaireItem
     */
    public static ArrayList<CommentaireItem> getCommentaires(final String unContenu, final String urlPage) {
        // mon retour
        ArrayList<CommentaireItem> mesCommentairesItem = new ArrayList<>();

        // Calcul du numéro de page
        int numeroPage = Integer.valueOf(
                urlPage.substring(urlPage.indexOf("&") + Constantes.NEXT_INPACT_URL_COMMENTAIRES_PARAM_NUM_PAGE.length() + 2));

        // Lancement du parseur sur la page
        Document pageNXI = Jsoup.parse(unContenu, urlPage);

        // ID de l'article concerné
        Element refArticle = pageNXI.select("aside[data-relnews]").get(0);
        int idArticle = Integer.valueOf(refArticle.attr("data-relnews"));

        // Les commentaires
        // Passage par une regexp => https://github.com/jhy/jsoup/issues/521
        Elements lesCommentaires = pageNXI.select("div[class~=actu_comm ],div[class~=actu_comm_author]");

        // Contenu
        // Supprimer les liens internes (<a> => <div>)
        // "En réponse à ...", "... à écrit"
        Elements lesLiensInternes = lesCommentaires.select("a[class=link_reply_to], div[class=quote_bloc]>div[class=qname]>a");
        lesLiensInternes.tagName("div");

        // Blockquote
        Elements lesCitations = lesCommentaires.select("div[class=link_reply_to], div[class=quote_bloc]");
        lesCitations.tagName("blockquote");

        // Gestion des URL relatives
        Elements lesLiens = lesCommentaires.select("a[href]");
        // Pour chaque lien
        for (Element unLien : lesLiens) {
            // Assignation de son URL absolue
            unLien.attr("href", unLien.absUrl("href"));
        }

        // Calcul de l'indice du premier commentaire (gestion des commentaires supprimés)
        int idCommPrecedent = (numeroPage - 1) * Constantes.NB_COMMENTAIRES_PAR_PAGE;
        int uuidCommPrecedent = 0;

        CommentaireItem monCommentaireItem;
        // Pour chaque commentaire
        for (Element unCommentaire : lesCommentaires) {
            monCommentaireItem = new CommentaireItem();

            // ID de l'article
            monCommentaireItem.setArticleId(idArticle);

            // UUID du commentaire
            int monUUID;
            try {
                monUUID = Integer.valueOf(unCommentaire.attr("data-content-id"));
            } catch (NumberFormatException e) {
                // Commentaire supprimé : UUID précédent + 1
                monUUID = uuidCommPrecedent + 1;
            }
            // Mise à jour de l'indice stocké
            uuidCommPrecedent = monUUID;
            // Enregistrement de l'UUID
            monCommentaireItem.setUuid(monUUID);

            // Auteur
            Elements monAuteur = unCommentaire.select("span[class=author_name]");
            if (!monAuteur.isEmpty()) {
                monCommentaireItem.setAuteur(monAuteur.get(0).text());
            } else {
                // Gestion des commentaires supprimés
                monCommentaireItem.setAuteur("-");
            }

            // Date
            Elements maDate = unCommentaire.select("span[class=date_comm]");
            if (!maDate.isEmpty()) {
                String laDate = maDate.get(0).text();
                monCommentaireItem.setTimeStampPublication(convertToTimeStamp(laDate, Constantes.FORMAT_DATE_COMMENTAIRE));
            } else {
                // Gestion des commentaires supprimés
                monCommentaireItem.setTimeStampPublication(0);
            }

            // Id du commentaire
            Elements monID = unCommentaire.select("span[class=actu_comm_num]");
            if (!monID.isEmpty()) {
                // Le premier caractère est un "#"
                String lID = monID.get(0).text().substring(1);
                monCommentaireItem.setId(Integer.valueOf(lID));
                // MàJ du numéro du dernier commentaire
                idCommPrecedent = Integer.valueOf(lID);
            } else {
                // Gestion des commentaires supprimés
                monCommentaireItem.setId(idCommPrecedent + 1);
                // MàJ du numéro du dernier commentaire
                idCommPrecedent++;
            }

            // Contenu
            Elements monContenu = unCommentaire.select("div[class=actu_comm_content]");
            if (!monContenu.isEmpty()) {
                monCommentaireItem.setCommentaire(monContenu.get(0).toString());
            } else {
                // Gestion des commentaires supprimés - Récupération de la chaîne du détail de modération
                monContenu = unCommentaire.select("div[class~=actu_comm_author]");
                if (!monContenu.isEmpty()) {
                    monCommentaireItem.setCommentaire(monContenu.get(0).toString());
                } else {
                    // Gestion de l'erreur de récupération de la modération (en cas de modif du code html évite une
                    // exception... !)
                    monCommentaireItem.setCommentaire("--- Erreur ---");
                }
            }

            // Et je le stocke
            mesCommentairesItem.add(monCommentaireItem);
        }

        return mesCommentairesItem;
    }

    /**
     * Convertit une date texte en timestamp.
     *
     * @param uneDate      date au format textuel
     * @param unFormatDate format de la date
     * @return timestamp
     */
    private static long convertToTimeStamp(final String uneDate, final String unFormatDate) {
        DateFormat dfm = new SimpleDateFormat(unFormatDate, Constantes.LOCALE);
        dfm.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        long laDateTS = 0;
        try {
            // Récupération du timestamp
            laDateTS = dfm.parse(uneDate).getTime();
        } catch (ParseException e) {
            if (Constantes.DEBUG) {
                Log.e("ParseurHTML", "convertToTimeStamp() - erreur parsage date : " + uneDate, e);
            }
        }

        return laDateTS;
    }
}